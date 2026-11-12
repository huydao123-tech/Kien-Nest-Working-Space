package com.cowork.business.service;

import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.config.PricingProperties;
import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.ResourceType;
import com.cowork.dataaccess.entity.Voucher;
import com.cowork.dataaccess.entity.VoucherRedemption;
import com.cowork.dataaccess.entity.VoucherResourceType;
import com.cowork.dataaccess.enums.ChargeType;
import com.cowork.dataaccess.enums.DiscountType;
import com.cowork.dataaccess.enums.ResourceTypeCode;
import com.cowork.dataaccess.enums.VoucherRedemptionStatus;
import com.cowork.dataaccess.enums.VoucherStatus;
import com.cowork.dataaccess.repository.ResourceTypeRepository;
import com.cowork.dataaccess.repository.VoucherRedemptionRepository;
import com.cowork.dataaccess.repository.VoucherRepository;
import com.cowork.dataaccess.valueobject.Money;
import com.cowork.dataaccess.valueobject.TimeRange;
import com.cowork.dto.CommerceDtos.VoucherDetail;
import com.cowork.dto.CommerceDtos.VoucherRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * UC05, UC21: voucher giảm giá.
 * <ul>
 *   <li>BR-12: mỗi đơn tối đa một voucher; voucher phải ACTIVE, đang trong [valid_from, valid_to), còn tổng lượt,
 *       còn lượt của khách, đúng loại chỗ, đơn đạt giá trị tối thiểu.</li>
 *   <li>FR-PROMO-03: lượt bị giữ ngay khi giữ chỗ ({@code HELD}), thành {@code USED} khi đơn được xác nhận,
 *       trả lại ({@code RELEASED}) khi đơn hết hạn / thanh toán thất bại / bị hủy.</li>
 * </ul>
 * Giá trị giảm tính trên tạm tính = tiền thuê + add-on và không bao giờ vượt tạm tính.
 */
@Service
@Transactional
public class VoucherService {

    private static final Set<VoucherRedemptionStatus> COUNTED = EnumSet.of(VoucherRedemptionStatus.HELD, VoucherRedemptionStatus.USED);

    private final VoucherRepository vouchers;
    private final VoucherRedemptionRepository redemptions;
    private final ResourceTypeRepository resourceTypes;
    private final PricingService pricing;
    private final PricingProperties pricingProperties;

    public VoucherService(VoucherRepository vouchers, VoucherRedemptionRepository redemptions,
                          ResourceTypeRepository resourceTypes, PricingService pricing, PricingProperties pricingProperties) {
        this.vouchers = vouchers;
        this.redemptions = redemptions;
        this.resourceTypes = resourceTypes;
        this.pricing = pricing;
        this.pricingProperties = pricingProperties;
    }

    /**
     * UC05: áp voucher cho đơn vừa giữ chỗ (đơn đã có tiền thuê + add-on). Giữ một lượt và trả về số tiền giảm.
     * Voucher bị khóa ghi trong lúc kiểm tra để hai đơn không cùng lấy lượt cuối.
     */
    public Money redeem(String code, Booking booking, LocalDateTime now) {
        Voucher voucher = vouchers.findByCodeForUpdate(code.trim())
                .orElseThrow(() -> new NotFoundException("Voucher", code.trim()));
        if (voucher.getStatus() != VoucherStatus.ACTIVE) {
            throw new DomainException("VOUCHER_INACTIVE", "Voucher " + voucher.getCode() + " đang tạm dừng");
        }
        if (now.isBefore(voucher.getValidFrom()) || !now.isBefore(voucher.getValidTo())) {
            throw new DomainException("VOUCHER_EXPIRED", "Voucher " + voucher.getCode() + " chỉ dùng từ "
                    + voucher.getValidFrom() + " đến trước " + voucher.getValidTo());
        }
        if (voucher.getTotalLimit() != null && voucher.getUsedCount() >= voucher.getTotalLimit()) {
            throw new ConflictException("VOUCHER_USED_UP", "Voucher " + voucher.getCode() + " đã hết lượt");
        }
        Long customerId = booking.getCustomer().getId();
        if (redemptions.countByVoucherIdAndCustomerIdAndStatusIn(voucher.getId(), customerId, COUNTED) >= voucher.getPerCustomerLimit()) {
            throw new DomainException("VOUCHER_CUSTOMER_LIMIT", "Bạn đã dùng hết lượt của voucher " + voucher.getCode());
        }
        ResourceTypeCode type = booking.getResource().getType();
        if (!appliesTo(voucher, type)) {
            throw new DomainException("VOUCHER_NOT_APPLICABLE", "Voucher " + voucher.getCode() + " không áp dụng cho loại chỗ " + type);
        }
        Money subtotal = subtotalOf(booking);
        if (subtotal.amount() < voucher.getMinOrderAmount()) {
            throw new DomainException("VOUCHER_MIN_ORDER", "Đơn phải từ " + voucher.getMinOrderAmount() + " VND mới dùng được voucher này");
        }
        Money discount = discountFor(voucher, type, booking.getPeriod(), subtotal);
        if (!discount.isPositive()) {
            throw new DomainException("VOUCHER_NOT_APPLICABLE", "Voucher " + voucher.getCode() + " không giảm được cho khung giờ này");
        }

        voucher.setUsedCount(voucher.getUsedCount() + 1);
        VoucherRedemption redemption = new VoucherRedemption(voucher, booking, booking.getCustomer(), discount.amount());
        redemption.setCreatedAt(now);
        redemptions.save(redemption);
        return discount;
    }

    /** Đơn được xác nhận: lượt đang giữ thành đã dùng. */
    public void markUsed(Booking booking) {
        redemptions.findByBookingId(booking.getId())
                .filter(r -> r.getStatus() == VoucherRedemptionStatus.HELD)
                .ifPresent(r -> r.setStatus(VoucherRedemptionStatus.USED));
    }

    /** FR-PROMO-03: đơn hết hạn / thất bại / bị hủy → trả lượt cho voucher. Gọi lại nhiều lần cũng chỉ trả một lần. */
    public void release(Booking booking) {
        redemptions.findByBookingId(booking.getId())
                .filter(r -> COUNTED.contains(r.getStatus()))
                .ifPresent(r -> {
                    r.setStatus(VoucherRedemptionStatus.RELEASED);
                    Voucher voucher = vouchers.findByIdForUpdate(r.getVoucher().getId())
                            .orElseThrow(() -> new NotFoundException("Voucher", r.getVoucher().getId()));
                    voucher.setUsedCount(Math.max(0, voucher.getUsedCount() - 1));
                });
    }

    /**
     * Đổi chỗ / khung giờ / thời lượng (booking.md Prompt 2): số tiền giảm của voucher đã áp, tính ở chỗ mới; chưa ghi gì.
     * Voucher phải áp dụng được cho loại chỗ mới và tạm tính mới phải đạt giá trị tối thiểu. Đơn không có voucher → 0.
     */
    @Transactional(readOnly = true)
    public Money quoteChange(Booking booking, ResourceTypeCode newType, TimeRange newPeriod, Money newSubtotal) {
        return redemptions.findByBookingId(booking.getId())
                .filter(r -> COUNTED.contains(r.getStatus()))
                .map(r -> {
                    Voucher voucher = r.getVoucher();
                    if (!appliesTo(voucher, newType)) {
                        throw new DomainException("VOUCHER_NOT_APPLICABLE",
                                "Voucher " + voucher.getCode() + " của đơn không áp dụng cho loại chỗ " + newType);
                    }
                    if (newSubtotal.amount() < voucher.getMinOrderAmount()) {
                        throw new DomainException("VOUCHER_MIN_ORDER", "Voucher " + voucher.getCode() + " cần đơn từ "
                                + voucher.getMinOrderAmount() + " VND, chỗ mới chỉ " + newSubtotal.amount() + " VND");
                    }
                    return discountFor(voucher, newType, newPeriod, newSubtotal);
                })
                .orElse(Money.ZERO);
    }

    /** Áp dụng đổi chỗ: ghi số tiền giảm mới vào lượt voucher của đơn. Đơn không có voucher thì không làm gì. */
    public void applyChange(Booking booking, Money discount) {
        redemptions.findByBookingId(booking.getId())
                .filter(r -> COUNTED.contains(r.getStatus()))
                .ifPresent(r -> r.setDiscountAmount(discount.amount()));
    }

    /** Voucher không giới hạn loại chỗ, hoặc có {@code type} trong danh sách loại chỗ áp dụng. */
    private static boolean appliesTo(Voucher voucher, ResourceTypeCode type) {
        return voucher.getResourceTypes().isEmpty()
                || voucher.getResourceTypes().stream().anyMatch(t -> t.getResourceType().getCode() == type);
    }

    /**
     * Số tiền giảm, không vượt tạm tính:
     * PERCENT = tạm tính × % (làm tròn xuống, tối đa {@code max_discount_amount});
     * AMOUNT = số tiền cố định; FREE_MINUTES = giá các block đầu tiên của khung giờ, số block = phút miễn phí / block (làm tròn xuống).
     */
    Money discountFor(Voucher voucher, ResourceTypeCode type, TimeRange period, Money subtotal) {
        long discount = switch (voucher.getDiscountType()) {
            case PERCENT -> {
                long value = subtotal.amount() * voucher.getDiscountValue() / 100;
                yield voucher.getMaxDiscountAmount() == null ? value : Math.min(value, voucher.getMaxDiscountAmount());
            }
            case AMOUNT -> voucher.getDiscountValue();
            case FREE_MINUTES -> freeMinutesValue(voucher.getDiscountValue(), type, period);
        };
        return Money.vnd(Math.min(discount, subtotal.amount()));
    }

    private long freeMinutesValue(long freeMinutes, ResourceTypeCode type, TimeRange period) {
        int blockMinutes = pricingProperties.blockMinutes();
        long bookedMinutes = period.duration().toMinutes();
        long minutes = Math.min(freeMinutes / blockMinutes * blockMinutes, bookedMinutes);
        if (minutes <= 0) {
            return 0;
        }
        return pricing.quote(type, new TimeRange(period.startAt(), period.startAt().plusMinutes(minutes))).total().amount();
    }

    /** Tạm tính = tiền thuê + add-on (chưa trừ giảm giá). */
    private static Money subtotalOf(Booking booking) {
        return booking.sumOf(ChargeType.RENTAL).plus(booking.sumOf(ChargeType.ADDON));
    }

    // ------------------------------------------------------------------
    // Quản trị voucher (UC21)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<VoucherDetail> list() {
        return vouchers.findAllByOrderByIdAsc().stream().map(VoucherDetail::from).toList();
    }

    @Transactional(readOnly = true)
    public VoucherDetail get(Long id) {
        return VoucherDetail.from(load(id));
    }

    public VoucherDetail create(VoucherRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (vouchers.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("CODE_ALREADY_EXISTS", "Mã voucher đã tồn tại: " + code);
        }
        Voucher voucher = new Voucher();
        voucher.setStatus(VoucherStatus.ACTIVE);
        apply(voucher, request, code);
        return VoucherDetail.from(vouchers.save(voucher));
    }

    /** {@code used_count} do hệ thống đếm, không sửa tay; tổng lượt mới không được nhỏ hơn số lượt đã dùng. */
    public VoucherDetail update(Long id, VoucherRequest request) {
        Voucher voucher = load(id);
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (!code.equals(voucher.getCode()) && vouchers.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("CODE_ALREADY_EXISTS", "Mã voucher đã tồn tại: " + code);
        }
        if (request.totalLimit() != null && request.totalLimit() < voucher.getUsedCount()) {
            throw new DomainException("VOUCHER_LIMIT_BELOW_USED", "Tổng lượt không được nhỏ hơn số lượt đã dùng (" + voucher.getUsedCount() + ")");
        }
        apply(voucher, request, code);
        return VoucherDetail.from(vouchers.save(voucher));
    }

    /** Voucher đã có người dùng thì không xóa được (409 DATA_CONFLICT) — chuyển PAUSED để ngưng. */
    public void delete(Long id) {
        vouchers.delete(load(id));
        vouchers.flush();
    }

    private void apply(Voucher voucher, VoucherRequest request, String code) {
        if (!request.validTo().isAfter(request.validFrom())) {
            throw new DomainException("INVALID_TIME_RANGE", "Ngày hết hạn phải sau ngày bắt đầu");
        }
        if (request.discountType() == DiscountType.PERCENT && request.discountValue() > 100) {
            throw new DomainException("INVALID_DISCOUNT", "Giảm theo % chỉ nhận 1–100");
        }
        voucher.setCode(code);
        voucher.setName(request.name().trim());
        voucher.setDiscountType(request.discountType());
        voucher.setDiscountValue(request.discountValue());
        voucher.setMinOrderAmount(request.minOrderAmount());
        voucher.setMaxDiscountAmount(request.maxDiscountAmount());
        voucher.setValidFrom(request.validFrom());
        voucher.setValidTo(request.validTo());
        voucher.setTotalLimit(request.totalLimit());
        voucher.setPerCustomerLimit(request.perCustomerLimit());
        if (request.status() != null) {
            voucher.setStatus(request.status());
        }
        syncResourceTypes(voucher, request.resourceTypes() == null ? List.of() : request.resourceTypes());
    }

    /** So khớp danh sách loại chỗ (bỏ dòng thừa, thêm dòng thiếu) thay vì xóa hết rồi thêm lại cùng khóa chính. */
    private void syncResourceTypes(Voucher voucher, List<ResourceTypeCode> wanted) {
        Set<ResourceTypeCode> codes = wanted.isEmpty() ? EnumSet.noneOf(ResourceTypeCode.class) : EnumSet.copyOf(wanted);
        voucher.getResourceTypes().removeIf(t -> !codes.contains(t.getResourceType().getCode()));
        voucher.getResourceTypes().forEach(t -> codes.remove(t.getResourceType().getCode()));
        for (ResourceTypeCode code : codes) {
            ResourceType type = resourceTypes.findByCode(code).orElseThrow(() -> new NotFoundException("Loại không gian", code));
            voucher.getResourceTypes().add(new VoucherResourceType(voucher, type));
        }
    }

    private Voucher load(Long id) {
        return vouchers.findById(id).orElseThrow(() -> new NotFoundException("Voucher", id));
    }
}
