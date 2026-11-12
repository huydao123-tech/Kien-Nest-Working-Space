package com.cowork.business.service;

import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.config.PricingProperties;
import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.MembershipPass;
import com.cowork.dataaccess.entity.MembershipPlan;
import com.cowork.dataaccess.entity.MembershipPlanResourceType;
import com.cowork.dataaccess.entity.MembershipUsage;
import com.cowork.dataaccess.entity.ResourceType;
import com.cowork.dataaccess.enums.MembershipPassStatus;
import com.cowork.dataaccess.enums.MembershipPlanStatus;
import com.cowork.dataaccess.enums.MembershipUsageStatus;
import com.cowork.dataaccess.enums.ResourceTypeCode;
import com.cowork.dataaccess.repository.MembershipPassRepository;
import com.cowork.dataaccess.repository.MembershipPlanRepository;
import com.cowork.dataaccess.repository.MembershipUsageRepository;
import com.cowork.dataaccess.repository.ResourceTypeRepository;
import com.cowork.dataaccess.valueobject.Money;
import com.cowork.dataaccess.valueobject.TimeRange;
import com.cowork.dto.CommerceDtos.MembershipPassResponse;
import com.cowork.dto.CommerceDtos.MembershipPlanDetail;
import com.cowork.dto.CommerceDtos.MembershipPlanRequest;
import com.cowork.dto.CommerceDtos.MembershipSummary;
import com.cowork.dto.PriceQuote;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * UC05, UC21: gói thành viên (Membership Pass) — số phút trả trước.
 * <ul>
 *   <li>BR-13: trừ phút của gói trước, phần còn lại trả tiền thường. Chỉ dùng cho loại chỗ gói cho phép.</li>
 *   <li>Trừ theo bội số block (30 phút), tối đa bằng thời lượng đơn đã làm tròn lên block. Chỉ trừ vào tiền thuê,
 *       không trừ vào add-on.</li>
 *   <li>Giá trị khấu trừ = giá thường của các block đầu khung giờ được gói phủ (đủ cả đơn = toàn bộ tiền thuê).</li>
 *   <li>Phút bị giữ ngay khi giữ chỗ ({@code HELD}), thành {@code USED} khi đơn xác nhận, trả lại ({@code RELEASED})
 *       khi đơn hết hạn / thất bại / bị hủy.</li>
 * </ul>
 */
@Service
@Transactional
public class MembershipService {

    private static final Set<MembershipUsageStatus> ACTIVE_USAGE = EnumSet.of(MembershipUsageStatus.HELD, MembershipUsageStatus.USED);

    private final MembershipPlanRepository plans;
    private final MembershipPassRepository passes;
    private final MembershipUsageRepository usages;
    private final ResourceTypeRepository resourceTypes;
    private final PricingService pricing;
    private final PricingProperties pricingProperties;
    private final Clock clock;

    public MembershipService(MembershipPlanRepository plans, MembershipPassRepository passes, MembershipUsageRepository usages,
                             ResourceTypeRepository resourceTypes, PricingService pricing, PricingProperties pricingProperties,
                             Clock clock) {
        this.plans = plans;
        this.passes = passes;
        this.usages = usages;
        this.resourceTypes = resourceTypes;
        this.pricing = pricing;
        this.pricingProperties = pricingProperties;
        this.clock = clock;
    }

    /**
     * BR-13: trừ phút của gói còn hiệu lực (hết hạn sớm nhất trước) cho đơn vừa giữ chỗ.
     * {@code rentalQuote} là báo giá tiền thuê của khung giờ đơn. Trả về giá trị khấu trừ quy ra tiền.
     */
    public Money deduct(Booking booking, PriceQuote rentalQuote, LocalDateTime now) {
        ResourceTypeCode type = booking.getResource().getType();
        int blockMinutes = pricingProperties.blockMinutes();
        int bookedMinutes = rentalQuote.blocks() * blockMinutes;
        MembershipPass pass = passes.findUsableForUpdate(booking.getCustomer().getId(), booking.getPeriod().startAt()).stream()
                .filter(p -> covers(p.getPlan(), type))
                .filter(p -> p.getRemainingMinutes() >= blockMinutes)
                .findFirst()
                .orElseThrow(() -> new DomainException("NO_USABLE_MEMBERSHIP",
                        "Không có gói thành viên còn hiệu lực, còn đủ phút cho loại chỗ " + type));
        int usable = pass.getRemainingMinutes() / blockMinutes * blockMinutes;
        int minutes = Math.min(usable, bookedMinutes);

        pass.setRemainingMinutes(pass.getRemainingMinutes() - minutes);
        if (pass.getRemainingMinutes() == 0) {
            pass.setStatus(MembershipPassStatus.USED_UP);
        }
        MembershipUsage usage = new MembershipUsage(pass, booking, minutes);
        usage.setUsedAt(now);
        usages.save(usage);
        return valueOf(minutes, bookedMinutes, type, booking.getPeriod(), rentalQuote.total());
    }

    /** Đơn được xác nhận: phút đang giữ thành đã dùng. */
    public void markUsed(Booking booking) {
        usages.findByBookingId(booking.getId()).stream()
                .filter(u -> u.getStatus() == MembershipUsageStatus.HELD)
                .forEach(u -> u.setStatus(MembershipUsageStatus.USED));
    }

    /** Đơn hết hạn / thất bại / bị hủy → trả phút lại cho gói. Gọi lại nhiều lần cũng chỉ trả một lần. */
    public void release(Booking booking) {
        for (MembershipUsage usage : usages.findByBookingId(booking.getId())) {
            if (!ACTIVE_USAGE.contains(usage.getStatus())) {
                continue;
            }
            usage.setStatus(MembershipUsageStatus.RELEASED);
            returnMinutes(usage, usage.getMinutes());
        }
    }

    /**
     * Đổi chỗ / khung giờ / thời lượng (booking.md Prompt 2): giá trị khấu trừ ở chỗ mới, chưa ghi gì.
     * Số phút đã trừ giữ nguyên, không trừ thêm khi thời lượng dài hơn; ngắn hơn thì chỉ tính tới thời lượng mới
     * (phần dư được trả lại gói ở {@link #applyChange}). Loại chỗ mới phải nằm trong gói. Đơn không dùng gói → 0.
     */
    @Transactional(readOnly = true)
    public Money quoteChange(Booking booking, ResourceTypeCode newType, TimeRange newPeriod, PriceQuote newRentalQuote) {
        List<MembershipUsage> active = activeUsages(booking);
        if (active.isEmpty()) {
            return Money.ZERO;
        }
        for (MembershipUsage usage : active) {
            if (!covers(usage.getPass().getPlan(), newType)) {
                throw new DomainException("MEMBERSHIP_NOT_APPLICABLE",
                        "Gói thành viên của đơn không dùng được cho loại chỗ " + newType);
            }
        }
        int bookedMinutes = newRentalQuote.blocks() * pricingProperties.blockMinutes();
        int minutes = Math.min(sumMinutes(active), bookedMinutes);
        return valueOf(minutes, bookedMinutes, newType, newPeriod, newRentalQuote.total());
    }

    /**
     * Áp dụng đổi chỗ: thời lượng mới ngắn hơn số phút đã trừ thì trả phần dư lại gói, bớt từ lần trừ sau cùng trước.
     * Một lần trừ bị bớt hết thì chuyển {@code RELEASED} (cột minutes phải > 0).
     */
    public void applyChange(Booking booking, PriceQuote newRentalQuote) {
        List<MembershipUsage> active = activeUsages(booking);
        int excess = sumMinutes(active) - newRentalQuote.blocks() * pricingProperties.blockMinutes();
        for (int i = active.size() - 1; i >= 0 && excess > 0; i--) {
            MembershipUsage usage = active.get(i);
            int giveBack = Math.min(excess, usage.getMinutes());
            if (giveBack == usage.getMinutes()) {
                usage.setStatus(MembershipUsageStatus.RELEASED);
            } else {
                usage.setMinutes(usage.getMinutes() - giveBack);
            }
            returnMinutes(usage, giveBack);
            excess -= giveBack;
        }
    }

    /**
     * FR-AUTH-05: các gói của khách cho trang hồ sơ. Gói ACTIVE đã quá hạn hiển thị EXPIRED (chưa có job đổi trạng thái);
     * số phút còn lại chỉ cộng các gói còn dùng được.
     */
    @Transactional(readOnly = true)
    public MembershipSummary summaryOf(Long customerId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<MembershipPassResponse> list = passes.findByCustomerIdOrderByExpiresAtAscIdAsc(customerId).stream()
                .map(p -> {
                    boolean expired = p.getStatus() == MembershipPassStatus.ACTIVE && !now.isBefore(p.getExpiresAt());
                    MembershipPassStatus status = expired ? MembershipPassStatus.EXPIRED : p.getStatus();
                    boolean usable = status == MembershipPassStatus.ACTIVE && p.getRemainingMinutes() > 0;
                    return new MembershipPassResponse(p.getId(), p.getPlan().getName(), p.getRemainingMinutes(),
                            p.getPurchasedAt(), p.getExpiresAt(), status, usable);
                })
                .toList();
        int remaining = list.stream().filter(MembershipPassResponse::usable).mapToInt(MembershipPassResponse::remainingMinutes).sum();
        return new MembershipSummary(remaining, list);
    }

    private List<MembershipUsage> activeUsages(Booking booking) {
        return usages.findByBookingId(booking.getId()).stream().filter(u -> ACTIVE_USAGE.contains(u.getStatus())).toList();
    }

    private static int sumMinutes(List<MembershipUsage> list) {
        return list.stream().mapToInt(MembershipUsage::getMinutes).sum();
    }

    /** Cộng {@code minutes} lại gói của {@code usage}; gói đã dùng hết thì mở lại (hoặc hết hạn nếu đã quá hạn). */
    private void returnMinutes(MembershipUsage usage, int minutes) {
        MembershipPass pass = passes.findByIdForUpdate(usage.getPass().getId())
                .orElseThrow(() -> new NotFoundException("Gói thành viên", usage.getPass().getId()));
        pass.setRemainingMinutes(pass.getRemainingMinutes() + minutes);
        if (pass.getStatus() == MembershipPassStatus.USED_UP) {
            boolean stillValid = LocalDateTime.now(clock).isBefore(pass.getExpiresAt());
            pass.setStatus(stillValid ? MembershipPassStatus.ACTIVE : MembershipPassStatus.EXPIRED);
        }
    }

    private Money valueOf(int minutes, int bookedMinutes, ResourceTypeCode type, TimeRange period, Money rental) {
        if (minutes >= bookedMinutes) {
            return rental;
        }
        Money covered = pricing.quote(type, new TimeRange(period.startAt(), period.startAt().plusMinutes(minutes))).total();
        return covered.amount() > rental.amount() ? rental : covered;
    }

    private static boolean covers(MembershipPlan plan, ResourceTypeCode type) {
        return plan.getResourceTypes().stream().anyMatch(t -> t.getResourceType().getCode() == type);
    }

    // ------------------------------------------------------------------
    // Quản trị gói (UC21, FR-ADM-06)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<MembershipPlanDetail> listPlans() {
        return plans.findAllByOrderByIdAsc().stream().map(MembershipPlanDetail::from).toList();
    }

    @Transactional(readOnly = true)
    public MembershipPlanDetail getPlan(Long id) {
        return MembershipPlanDetail.from(plan(id));
    }

    public MembershipPlanDetail createPlan(MembershipPlanRequest request) {
        MembershipPlan plan = new MembershipPlan();
        plan.setStatus(MembershipPlanStatus.ACTIVE);
        apply(plan, request);
        return MembershipPlanDetail.from(plans.save(plan));
    }

    /** Sửa gói không ảnh hưởng gói khách đã mua (số phút, hạn dùng đã ghi vào membership_pass). */
    public MembershipPlanDetail updatePlan(Long id, MembershipPlanRequest request) {
        MembershipPlan plan = plan(id);
        apply(plan, request);
        return MembershipPlanDetail.from(plans.save(plan));
    }

    /** Gói đã có khách mua thì không xóa được (409 DATA_CONFLICT) — chuyển STOPPED để ngưng bán. */
    public void deletePlan(Long id) {
        plans.delete(plan(id));
        plans.flush();
    }

    private void apply(MembershipPlan plan, MembershipPlanRequest request) {
        plan.setName(request.name().trim());
        plan.setTotalMinutes(request.totalMinutes());
        plan.setValidDays(request.validDays());
        plan.setPrice(request.price());
        if (request.status() != null) {
            plan.setStatus(request.status());
        }
        Set<ResourceTypeCode> codes = EnumSet.copyOf(request.resourceTypes());
        plan.getResourceTypes().removeIf(t -> !codes.contains(t.getResourceType().getCode()));
        plan.getResourceTypes().forEach(t -> codes.remove(t.getResourceType().getCode()));
        for (ResourceTypeCode code : codes) {
            ResourceType type = resourceTypes.findByCode(code).orElseThrow(() -> new NotFoundException("Loại không gian", code));
            plan.getResourceTypes().add(new MembershipPlanResourceType(plan, type));
        }
    }

    private MembershipPlan plan(Long id) {
        return plans.findById(id).orElseThrow(() -> new NotFoundException("Gói thành viên", id));
    }
}
