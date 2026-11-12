package com.cowork.business.service;

import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.business.service.AddonService.AddonLine;
import com.cowork.config.BookingProperties;
import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.BookingAddon;
import com.cowork.dataaccess.entity.BookingCharge;
import com.cowork.dataaccess.entity.BookingStatusHistory;
import com.cowork.dataaccess.entity.Branch;
import com.cowork.dataaccess.entity.Customer;
import com.cowork.dataaccess.entity.SpaceResource;
import com.cowork.dataaccess.enums.BookingStatus;
import com.cowork.dataaccess.enums.ChargeType;
import com.cowork.dataaccess.repository.BookingRepository;
import com.cowork.dataaccess.repository.SpaceResourceRepository;
import com.cowork.dataaccess.valueobject.Money;
import com.cowork.dataaccess.valueobject.TimeRange;
import com.cowork.dto.Actor;
import com.cowork.dto.BookingDtos.BookingFilter;
import com.cowork.dto.BookingDtos.HoldBookingRequest;
import com.cowork.dto.BookingDtos.RescheduleRequest;
import com.cowork.dto.PriceQuote;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static com.cowork.dataaccess.enums.BookingStatus.CANCELLED;
import static com.cowork.dataaccess.enums.BookingStatus.CHECKED_IN;
import static com.cowork.dataaccess.enums.BookingStatus.CHECKED_OUT;
import static com.cowork.dataaccess.enums.BookingStatus.COMPLETED;
import static com.cowork.dataaccess.enums.BookingStatus.CONFIRMED;
import static com.cowork.dataaccess.enums.BookingStatus.EXPIRED;
import static com.cowork.dataaccess.enums.BookingStatus.FAILED;
import static com.cowork.dataaccess.enums.BookingStatus.HELD;
import static com.cowork.dataaccess.enums.BookingStatus.NO_SHOW;
import static com.cowork.dataaccess.enums.BookingStatus.OVERSTAY;
import static com.cowork.dataaccess.enums.BookingStatus.REFUND_PENDING;

/**
 * Toàn bộ nghiệp vụ đơn đặt chỗ — xem State Diagram 3.1 trong docs/diagrams.md.
 * Mỗi hàm public là một chức năng: lấy đơn → kiểm tra quy tắc → đổi dữ liệu → lưu → (nếu cần) nhắn tin cho khách.
 *
 * <p>Chống double-booking (BR-01): mọi thao tác có thể chiếm thêm thời gian của một vị trí
 * (giữ chỗ, gia hạn, đổi khung giờ, kích hoạt lại đơn) đều khóa hàng {@link SpaceResource} trước khi kiểm tra chồng lấn,
 * nên các yêu cầu trên cùng vị trí được xử lý lần lượt. Thứ tự khóa luôn là: vị trí → add-on (id tăng dần) → voucher
 * → gói thành viên, để hai giao dịch không khóa chéo nhau.
 *
 * <p>Tiền của đơn (FR-BOOK-06) nằm trong {@code booking_charge}:
 * tổng = tiền thuê theo block + add-on − voucher − khấu trừ membership.
 */
@Service
@Transactional
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int MAX_PAGE_SIZE = 100;

    private final BookingRepository bookings;
    private final SpaceResourceRepository resources;
    private final ResourceService resourceService;
    private final PricingService pricing;
    private final CustomerService customers;
    private final NotificationService notifications;
    private final AddonService addons;
    private final VoucherService vouchers;
    private final MembershipService memberships;
    private final RefundService refunds;
    private final Clock clock;
    private final BookingProperties properties;
    private final BookingWindowPolicy windowPolicy;

    public BookingService(BookingRepository bookings, SpaceResourceRepository resources, ResourceService resourceService,
                          PricingService pricing, CustomerService customers, NotificationService notifications,
                          AddonService addons, VoucherService vouchers, MembershipService memberships, RefundService refunds,
                          Clock clock, BookingProperties properties, BookingWindowPolicy windowPolicy) {
        this.bookings = bookings;
        this.resources = resources;
        this.resourceService = resourceService;
        this.pricing = pricing;
        this.customers = customers;
        this.notifications = notifications;
        this.addons = addons;
        this.vouchers = vouchers;
        this.memberships = memberships;
        this.refunds = refunds;
        this.clock = clock;
        this.properties = properties;
        this.windowPolicy = windowPolicy;
    }

    // ------------------------------------------------------------------
    // Đặt chỗ và thanh toán
    // ------------------------------------------------------------------

    /** UC03 cho khách vãng lai (không đăng nhập). */
    public Booking hold(HoldBookingRequest request) {
        return hold(request, Actor.GUEST);
    }

    /**
     * UC03: giữ chỗ tạm 10 phút (BR-02) trong lúc khách thanh toán, kèm add-on (FR-BOOK-02), voucher (BR-12)
     * hoặc giờ membership (BR-13). Đơn không còn gì phải trả (membership phủ hết) thì xác nhận ngay.
     */
    public Booking hold(HoldBookingRequest request, Actor actor) {
        LocalDateTime now = now();
        SpaceResource resource = lockResource(request.resourceId());
        Branch branch = branchOf(resource);
        // Cùng quy tắc với lúc tìm chỗ (UC02): khung giờ hợp lệ, đủ thời lượng tối thiểu, trong giờ mở cửa chi nhánh.
        TimeRange period = windowPolicy.validate(request.startAt(), request.endAt(), branch);
        resourceService.requireBookable(resource, request.partySize());
        if (bookings.existsOccupying(resource.getId(), period, null)) {
            throw new ConflictException("SLOT_UNAVAILABLE", "Vị trí " + resource.getCode() + " đã có người đặt trong khung giờ này");
        }
        boolean useVoucher = request.voucherCode() != null && !request.voucherCode().isBlank();
        if (useVoucher && request.useMembership()) {
            throw new DomainException("VOUCHER_MEMBERSHIP_CONFLICT", "Không dùng chung voucher với giờ thành viên trong một đơn");
        }
        if (request.useMembership() && !actor.isCustomer()) {
            throw new DomainException("MEMBERSHIP_LOGIN_REQUIRED", "Đăng nhập tài khoản thành viên để dùng giờ của gói");
        }
        Customer customer = customerFor(request, actor);
        PriceQuote rental = pricing.quote(resource.getType(), period);
        List<AddonLine> addonLines = addons.reserve(branch, period, rental.blocks(), request.addons());

        Booking booking = new Booking();
        booking.setCode(generateCode());
        booking.setQrToken(generateQrToken());
        booking.setResource(resource);
        booking.setCustomer(customer);
        booking.setPeriod(period);
        booking.setPartySize(request.partySize());
        booking.setHoldExpiresAt(now.plus(properties.holdDuration()));
        booking.setCreatedAt(now);
        booking.getCharges().add(BookingCharge.of(booking, ChargeType.RENTAL, rental.total(), period, now));
        for (AddonLine line : addonLines) {
            booking.getAddons().add(new BookingAddon(booking, line.addon(), line.quantity(), line.unitPrice()));
            booking.getCharges().add(BookingCharge.of(booking, ChargeType.ADDON, line.amount(), period, now));
        }
        changeStatus(booking, HELD, now);
        // Lưu trước để có id — dòng voucher_redemption / membership_usage trỏ tới đơn.
        booking = bookings.save(booking);

        if (useVoucher) {
            addDeduction(booking, ChargeType.VOUCHER_DISCOUNT, vouchers.redeem(request.voucherCode(), booking, now), now);
        }
        if (request.useMembership()) {
            addDeduction(booking, ChargeType.MEMBERSHIP_DEDUCTION, memberships.deduct(booking, rental, now), now);
        }
        if (!booking.getTotal().isPositive()) {
            // Không còn khoản nào phải trả → không cần qua cổng thanh toán.
            changeStatus(booking, CONFIRMED, now);
            markBenefitsUsed(booking);
            booking = bookings.save(booking);
            notifications.bookingConfirmed(booking);
            return booking;
        }
        return bookings.save(booking);
    }

    /**
     * UC04, UC25: {@link PaymentService} gọi khi cổng báo thanh toán thành công, kể cả khi webhook về trễ.
     * Đơn HELD đã quá hạn nhưng job chưa chạy vẫn đang giữ chỗ, nên xác nhận lúc đó vẫn an toàn.
     */
    public void onPaymentSucceeded(Long bookingId, Money paid) {
        Booking booking = load(bookingId);
        BookingStatus status = booking.getStatus();

        if (status == HELD) {
            confirm(booking, paid);
            notifications.bookingConfirmed(booking);
        } else if (status == EXPIRED || status == FAILED) {
            // Webhook về trễ: chỉ xác nhận lại được nếu chưa ai đặt chỗ đó (BR-11). Đơn có voucher / membership thì
            // lượt / phút đã được trả lại lúc hết hạn và có thể đã bị người khác dùng, nên không kích hoạt lại mà hoàn tiền.
            lockResource(booking.getResource().getId());
            boolean slotTaken = bookings.existsOccupying(booking.getResource().getId(), booking.getPeriod(), booking.getId());
            if (slotTaken || hasDeductions(booking)) {
                booking.setPaidAmount(paid.amount());
                changeStatus(booking, REFUND_PENDING, now());
                bookings.save(booking);
                notifications.refundAfterLatePayment(booking, paid);
            } else {
                confirm(booking, paid);
                notifications.bookingConfirmed(booking);
            }
        } else if (status == CONFIRMED && booking.hasPendingChange()) {
            // Tiền bù cho yêu cầu đổi chỗ (booking.md Prompt 2). Còn ghi yêu cầu = job chưa hủy nên chỗ mới vẫn đang giữ.
            applyPendingChange(booking, paid);
        } else if (status == CONFIRMED) {
            refundUnexpectedPayment(booking, paid, now());
        } else {
            log.info("Bỏ qua thanh toán thành công trùng lặp cho đơn {} ở trạng thái {}", bookingId, status);
        }
    }

    /** {@link PaymentService} gọi khi cổng báo thanh toán thất bại. Trả lại lượt voucher / phút membership (FR-PAY-05). */
    public void onPaymentFailed(Long bookingId) {
        Booking booking = load(bookingId);
        if (booking.getStatus() == CONFIRMED && booking.hasPendingChange()) {
            // Trả phần chênh đổi chỗ không thành công: bỏ yêu cầu đổi, đơn giữ nguyên chỗ cũ.
            clearPendingChange(booking);
            bookings.save(booking);
            notifications.rescheduleCancelled(booking);
            return;
        }
        if (booking.getStatus() != HELD) {
            log.info("Bỏ qua thanh toán thất bại cho đơn {} ở trạng thái {}", bookingId, booking.getStatus());
            return;
        }
        changeStatus(booking, FAILED, now());
        releaseBenefits(booking);
        bookings.save(booking);
        notifications.paymentFailed(booking);
    }

    /**
     * UC06: hủy đơn đã thanh toán (CONFIRMED). Phải hủy trước giờ bắt đầu ít nhất {@code booking.change-notice} (2 giờ),
     * được hoàn 100% số tiền đã trả; trả lại lượt voucher và phút membership. Trả về số tiền hoàn.
     */
    public Money cancel(Long bookingId, Actor actor) {
        Booking booking = loadFor(bookingId, actor);
        requireStatus(booking, Set.of(CONFIRMED), "hủy");
        LocalDateTime now = now();
        requireBeforeChangeDeadline(booking, now, "hủy");

        Money refund = booking.getPaid();
        clearPendingChange(booking);
        changeStatus(booking, CANCELLED, now);
        releaseBenefits(booking);
        refunds.request(booking, refund, "CUSTOMER_CANCELLED", now);
        bookings.save(booking);
        notifications.bookingCancelled(booking, refund);
        return refund;
    }

    /**
     * Đổi chỗ trước check-in (booking.md Prompt 2): đổi vị trí, khung giờ và / hoặc thời lượng của đơn đã thanh toán
     * (CONFIRMED), trước giờ bắt đầu hiện tại ít nhất {@code booking.change-notice} (2 giờ). Chỗ mới phải đặt được như lúc
     * giữ chỗ (giờ mở cửa, sức chứa, còn trống, add-on còn đủ). Tiền tính lại theo chỗ mới:
     * <ul>
     *   <li>không cao hơn số đã trả → đổi ngay, hoàn phần chênh (nếu có);</li>
     *   <li>cao hơn → giữ chỗ mới {@code booking.hold-duration} (10 phút) chờ khách trả phần chênh. Trả xong mới đổi
     *       ({@link #onPaymentSucceeded}); thanh toán lỗi / quá hạn thì đơn giữ nguyên chỗ cũ.</li>
     * </ul>
     */
    public RescheduleResult reschedule(Long bookingId, RescheduleRequest request, Actor actor) {
        Booking booking = loadFor(bookingId, actor);
        requireStatus(booking, Set.of(CONFIRMED), "đổi chỗ");
        LocalDateTime now = now();
        requireBeforeChangeDeadline(booking, now, "đổi chỗ");
        if (booking.hasPendingChange() && now.isBefore(booking.getPendingExpiresAt())) {
            throw new ConflictException("RESCHEDULE_PAYMENT_PENDING",
                    "Đơn đang có yêu cầu đổi chỗ chờ thanh toán tới " + booking.getPendingExpiresAt());
        }

        Long targetId = request.resourceId() == null ? booking.getResource().getId() : request.resourceId();
        SpaceResource target = lockResource(targetId);
        TimeRange newPeriod = windowPolicy.validate(request.startAt(), request.endAt(), branchOf(target));
        if (target.getId().equals(booking.getResource().getId()) && newPeriod.equals(booking.getPeriod())) {
            throw new DomainException("RESCHEDULE_SAME_SLOT", "Vị trí và khung giờ mới trùng với hiện tại");
        }
        resourceService.requireBookable(target, booking.getPartySize());
        if (bookings.existsOccupying(target.getId(), newPeriod, booking.getId())) {
            throw new ConflictException("SLOT_UNAVAILABLE", "Vị trí " + target.getCode() + " đã có người đặt trong khung giờ mới");
        }
        ChangeQuote quote = quoteChange(booking, target, newPeriod);

        if (quote.total().amount() > booking.getPaidAmount()) {
            long due = quote.total().amount() - booking.getPaidAmount();
            booking.setPendingResource(target);
            booking.setPendingStartAt(newPeriod.startAt());
            booking.setPendingEndAt(newPeriod.endAt());
            booking.setPendingAmount(due);
            booking.setPendingExpiresAt(now.plus(properties.holdDuration()));
            return new RescheduleResult(bookings.save(booking), Money.vnd(due), Money.ZERO);
        }
        Money refund = applyChange(booking, target, newPeriod, quote, now);
        booking = bookings.save(booking);
        notifications.bookingRescheduled(booking, refund);
        return new RescheduleResult(booking, Money.ZERO, refund);
    }

    /**
     * Kết quả đổi chỗ. {@code amountDue} > 0: chỗ mới đang được giữ chờ khách trả phần chênh.
     * {@code refund} > 0: đã đổi xong, chỗ mới rẻ hơn số đã trả.
     */
    public record RescheduleResult(Booking booking, Money amountDue, Money refund) {

        public boolean paymentRequired() {
            return amountDue.isPositive();
        }
    }

    /** Giá của đơn ở chỗ mới; {@code addonAmounts} theo đúng thứ tự {@code booking.getAddons()}. */
    private record ChangeQuote(PriceQuote rental, List<Money> addonAmounts, Money voucherDiscount,
                               Money membershipDeduction, Money total) {
    }

    /**
     * Tính tiền đơn ở chỗ mới, chưa ghi gì: tiền thuê theo loại chỗ mới, add-on = đơn giá lúc đặt × số block mới × số lượng,
     * trừ voucher / phút gói của đơn (tính lại theo chỗ mới).
     */
    private ChangeQuote quoteChange(Booking booking, SpaceResource target, TimeRange newPeriod) {
        addons.recheck(booking, branchOf(target), newPeriod);
        PriceQuote rental = pricing.quote(target.getType(), newPeriod);
        List<Money> addonAmounts = booking.getAddons().stream()
                .map(a -> Money.vnd(a.getUnitPrice() * rental.blocks() * a.getQuantity()))
                .toList();
        Money subtotal = addonAmounts.stream().reduce(rental.total(), Money::plus);
        Money voucherDiscount = vouchers.quoteChange(booking, target.getType(), newPeriod, subtotal);
        Money membershipDeduction = memberships.quoteChange(booking, target.getType(), newPeriod, rental);
        Money deductions = voucherDiscount.plus(membershipDeduction);
        Money total = deductions.amount() >= subtotal.amount() ? Money.ZERO : subtotal.minus(deductions);
        return new ChangeQuote(rental, addonAmounts, voucherDiscount, membershipDeduction, total);
    }

    /**
     * Ghi chỗ mới vào đơn: vị trí, khung giờ, tiền từng dòng, lượt voucher, phút gói; xóa yêu cầu đang chờ.
     * Số đã trả cao hơn tổng mới thì hoàn phần chênh. Trả về số tiền hoàn.
     */
    private Money applyChange(Booking booking, SpaceResource target, TimeRange newPeriod, ChangeQuote quote, LocalDateTime now) {
        vouchers.applyChange(booking, quote.voucherDiscount());
        memberships.applyChange(booking, quote.rental());
        int addonLine = 0;
        for (BookingCharge charge : booking.getCharges()) {
            charge.setStartAt(newPeriod.startAt());
            charge.setEndAt(newPeriod.endAt());
            switch (charge.getType()) {
                case RENTAL -> charge.setAmount(quote.rental().total().amount());
                case ADDON -> charge.setAmount(quote.addonAmounts().get(addonLine++).amount());
                case VOUCHER_DISCOUNT -> charge.setAmount(quote.voucherDiscount().amount());
                case MEMBERSHIP_DEDUCTION -> charge.setAmount(quote.membershipDeduction().amount());
                default -> {
                    // EXTENSION, OVERSTAY_SURCHARGE chỉ có sau check-in, đơn CONFIRMED không có
                }
            }
        }
        booking.setResource(target);
        booking.setPeriod(newPeriod);
        clearPendingChange(booking);
        if (booking.getPaidAmount() <= quote.total().amount()) {
            return Money.ZERO;
        }
        Money refund = Money.vnd(booking.getPaidAmount() - quote.total().amount());
        booking.setPaidAmount(quote.total().amount());
        refunds.request(booking, refund, "RESCHEDULE_PRICE_DIFF", now);
        return refund;
    }

    /**
     * Khách đã trả phần chênh: đổi đơn sang chỗ đang giữ. Số tiền khác phần chênh (giao dịch cũ của một yêu cầu trước)
     * thì không đổi mà hoàn lại số đó.
     */
    private void applyPendingChange(Booking booking, Money paid) {
        LocalDateTime now = now();
        if (paid.amount() != booking.getPendingAmount()) {
            refundUnexpectedPayment(booking, paid, now);
            return;
        }
        SpaceResource target = lockResource(booking.getPendingResource().getId());
        TimeRange newPeriod = booking.getPendingPeriod();
        ChangeQuote quote = quoteChange(booking, target, newPeriod);
        booking.setPaidAmount(booking.getPaidAmount() + paid.amount());
        Money refund = applyChange(booking, target, newPeriod, quote, now);
        bookings.save(booking);
        notifications.bookingRescheduled(booking, refund);
    }

    /** Tiền về cho đơn đã xác nhận mà không còn gì phải trả (yêu cầu đổi chỗ đã hết hạn / bị hủy) → hoàn lại. */
    private void refundUnexpectedPayment(Booking booking, Money paid, LocalDateTime now) {
        refunds.request(booking, paid, "UNEXPECTED_PAYMENT", now);
        notifications.refundAfterLatePayment(booking, paid);
    }

    private static void clearPendingChange(Booking booking) {
        booking.setPendingResource(null);
        booking.setPendingStartAt(null);
        booking.setPendingEndAt(null);
        booking.setPendingAmount(null);
        booking.setPendingExpiresAt(null);
    }

    // ------------------------------------------------------------------
    // Sử dụng chỗ: check-in, gia hạn, check-out
    // ------------------------------------------------------------------

    /** UC07: check-in bằng QR, sớm nhất 15 phút trước giờ bắt đầu (BR-17). */
    public Booking checkIn(String qrToken) {
        Booking booking = loadByQr(qrToken);
        requireStatus(booking, Set.of(CONFIRMED), "check-in");

        LocalDateTime now = now();
        LocalDateTime earliest = booking.getPeriod().startAt().minus(properties.checkInEarlyWindow());
        if (now.isBefore(earliest)) {
            throw new DomainException("CHECK_IN_TOO_EARLY", "Chỉ được check-in từ " + earliest);
        }
        if (!now.isBefore(booking.getPeriod().endAt())) {
            throw new DomainException("CHECK_IN_TOO_LATE", "Khung giờ đặt chỗ đã kết thúc");
        }
        booking.setCheckedInAt(now);
        changeStatus(booking, CHECKED_IN, now);
        return bookings.save(booking);
    }

    /** UC08: gia hạn tại chỗ, chỉ khi vị trí còn trống trong khoảng gia hạn (BR-07). */
    public Booking extend(Long bookingId, LocalDateTime newEndAt) {
        Booking booking = load(bookingId);
        requireStatus(booking, Set.of(CHECKED_IN, OVERSTAY), "gia hạn");
        LocalDateTime oldEndAt = booking.getPeriod().endAt();
        if (newEndAt == null || !newEndAt.isAfter(oldEndAt)) {
            throw new DomainException("INVALID_EXTENSION", "Giờ kết thúc mới phải sau " + oldEndAt);
        }
        TimeRange extension = new TimeRange(oldEndAt, newEndAt);

        SpaceResource resource = lockResource(booking.getResource().getId());
        if (bookings.existsOccupying(resource.getId(), extension, booking.getId())) {
            throw new ConflictException("EXTENSION_UNAVAILABLE",
                    "Vị trí đã có khách đặt tiếp, không thể gia hạn tới " + newEndAt);
        }
        Money price = pricing.quote(resource.getType(), extension).total();

        LocalDateTime now = now();
        booking.setPeriod(booking.getPeriod().withEnd(newEndAt));
        booking.getCharges().add(BookingCharge.of(booking, ChargeType.EXTENSION, price, extension, now));
        if (booking.getStatus() == OVERSTAY && newEndAt.isAfter(now)) {
            changeStatus(booking, CHECKED_IN, now);
        }
        return bookings.save(booking);
    }

    /** UC09: check-out bằng QR; ra quá giờ (sau 5 phút ân hạn) thì tính phụ phí theo block (BR-06). */
    public Booking checkOut(String qrToken) {
        Booking booking = loadByQr(qrToken);
        requireStatus(booking, Set.of(CHECKED_IN, OVERSTAY), "check-out");

        LocalDateTime now = now();
        LocalDateTime endAt = booking.getPeriod().endAt();
        if (now.isAfter(endAt.plus(properties.overstayGrace()))) {
            TimeRange overstay = new TimeRange(endAt, now);
            Money surcharge = pricing.overstaySurcharge(booking.getResource().getType(), overstay);
            if (surcharge.isPositive()) {
                booking.getCharges().add(BookingCharge.of(booking, ChargeType.OVERSTAY_SURCHARGE, surcharge, overstay, now));
            }
        }

        booking.setCheckedOutAt(now);
        changeStatus(booking, booking.getOutstanding().isPositive() ? CHECKED_OUT : COMPLETED, now);
        return bookings.save(booking);
    }

    /** UC16: thu nốt khoản phát sinh (gia hạn, phụ phí) tại quầy. */
    public Booking settleOutstanding(Long bookingId, Money amount) {
        Booking booking = load(bookingId);
        requireStatus(booking, Set.of(CHECKED_OUT), "thanh toán khoản phát sinh");
        if (!amount.equals(booking.getOutstanding())) {
            throw new DomainException("AMOUNT_MISMATCH", "Số tiền cần thu là " + booking.getOutstanding().amount());
        }
        booking.setPaidAmount(booking.getPaidAmount() + amount.amount());
        changeStatus(booking, COMPLETED, now());
        return bookings.save(booking);
    }

    // ------------------------------------------------------------------
    // Xem đơn
    // ------------------------------------------------------------------

    /** UC06: chi tiết một đơn. Khách chỉ xem được đơn của mình (đơn của người khác trả 404 như không tồn tại). */
    @Transactional(readOnly = true)
    public Booking get(Long bookingId, Actor actor) {
        return loadFor(bookingId, actor);
    }

    /** Dùng nội bộ giữa các service (đã kiểm tra quyền ở nơi gọi). */
    @Transactional(readOnly = true)
    public Booking get(Long bookingId) {
        return load(bookingId);
    }

    /** "Đơn của tôi": đơn của khách đang đăng nhập, mới nhất trước, lọc tùy chọn theo trạng thái. */
    @Transactional(readOnly = true)
    public Page<Booking> listMine(Actor actor, BookingStatus status, int page, int size) {
        if (!actor.isCustomer()) {
            throw new DomainException("CUSTOMER_ONLY", "Chức năng chỉ dành cho khách có tài khoản");
        }
        return bookings.findAll(specOf(new BookingFilter(actor.id(), null, status, null, null)), pageOf(page, size));
    }

    /** Nhân viên xem toàn bộ đơn, lọc tùy chọn theo khách (id hoặc SĐT), trạng thái, khoảng giờ bắt đầu [from, to). */
    @Transactional(readOnly = true)
    public Page<Booking> search(BookingFilter filter, int page, int size) {
        if (filter.from() != null && filter.to() != null && !filter.to().isAfter(filter.from())) {
            throw new DomainException("INVALID_TIME_RANGE", "Thời điểm 'to' phải sau 'from'");
        }
        return bookings.findAll(specOf(filter), pageOf(page, size));
    }

    private static Specification<Booking> specOf(BookingFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (filter.customerId() != null) {
                where.add(cb.equal(root.get("customer").get("id"), filter.customerId()));
            }
            if (filter.phone() != null && !filter.phone().isBlank()) {
                where.add(cb.equal(root.get("customer").get("contact").get("phone"), filter.phone().trim()));
            }
            if (filter.status() != null) {
                where.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.from() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("period").get("startAt"), filter.from()));
            }
            if (filter.to() != null) {
                where.add(cb.lessThan(root.get("period").get("startAt"), filter.to()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private static PageRequest pageOf(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new DomainException("INVALID_PAGE", "page >= 0, size từ 1 đến " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("period.startAt"), Sort.Order.desc("id")));
    }

    // ------------------------------------------------------------------
    // Việc chạy định kỳ (BookingJobs gọi) — UC24
    // ------------------------------------------------------------------

    /** Giải phóng chỗ của đơn giữ chỗ quá hạn. Gọi riêng cho từng đơn để một đơn lỗi không làm hỏng cả lượt quét. */
    public void expireHold(Long bookingId) {
        Booking booking = load(bookingId);
        requireStatus(booking, Set.of(HELD), "hết hạn giữ chỗ");
        if (now().isBefore(booking.getHoldExpiresAt())) {
            throw new DomainException("HOLD_NOT_EXPIRED", "Đơn vẫn còn trong thời gian giữ chỗ");
        }
        changeStatus(booking, EXPIRED, now());
        releaseBenefits(booking);
        bookings.save(booking);
        notifications.holdExpired(booking);
    }

    /** Hủy yêu cầu đổi chỗ quá hạn thanh toán phần chênh: nhả chỗ mới, đơn giữ nguyên chỗ cũ (booking.md Prompt 2). */
    public void expirePendingChange(Long bookingId) {
        Booking booking = load(bookingId);
        if (!booking.hasPendingChange()) {
            throw new DomainException("NO_PENDING_CHANGE", "Đơn không có yêu cầu đổi chỗ đang chờ thanh toán");
        }
        if (now().isBefore(booking.getPendingExpiresAt())) {
            throw new DomainException("PENDING_CHANGE_NOT_EXPIRED", "Yêu cầu đổi chỗ vẫn còn trong thời gian chờ thanh toán");
        }
        clearPendingChange(booking);
        bookings.save(booking);
        notifications.rescheduleCancelled(booking);
    }

    @Transactional(readOnly = true)
    public List<Long> findExpiredPendingChangeIds() {
        return bookings.findPendingChangeIdsExpiredAt(now());
    }

    /** FR-SYS-02: đánh dấu khách đang ngồi quá giờ kết thúc (sau thời gian ân hạn) và báo cho lễ tân của chi nhánh. */
    public void flagOverstay(Long bookingId) {
        Booking booking = load(bookingId);
        LocalDateTime now = now();
        boolean overdue = booking.getStatus() == CHECKED_IN
                && now.isAfter(booking.getPeriod().endAt().plus(properties.overstayGrace()));
        if (overdue) {
            changeStatus(booking, OVERSTAY, now);
            bookings.save(booking);
            notifications.overstay(booking);
        }
    }

    /**
     * UC24, FR-CHK-09, FR-SYS-03, BR-16: đơn đã thanh toán mà khách không check-in sau thời gian chờ thì chuyển NO_SHOW,
     * không hoàn tiền, không trả lượt voucher / phút gói; chỗ được giải phóng vì NO_SHOW không còn chiếm vị trí (BR-01).
     * Thời gian chờ = min({@code booking.no-show-grace} 30 phút, {@code booking.no-show-grace-percent} 50% thời lượng đơn).
     * Đúng mốc hết thời gian chờ thì chưa đánh dấu (giống ân hạn quá giờ). Đơn chưa tới hạn / không còn CONFIRMED thì bỏ qua.
     */
    public void markNoShow(Long bookingId) {
        Booking booking = load(bookingId);
        LocalDateTime now = now();
        if (booking.getStatus() != CONFIRMED || !now.isAfter(noShowDeadline(booking))) {
            return;
        }
        clearPendingChange(booking);
        changeStatus(booking, NO_SHOW, now);
        bookings.save(booking);
        notifications.noShow(booking);
    }

    /** BR-16: mốc hết thời gian chờ check-in của đơn. */
    private LocalDateTime noShowDeadline(Booking booking) {
        Duration percentOfDuration = booking.getPeriod().duration().multipliedBy(properties.noShowGracePercent()).dividedBy(100);
        Duration grace = percentOfDuration.compareTo(properties.noShowGrace()) < 0 ? percentOfDuration : properties.noShowGrace();
        return booking.getPeriod().startAt().plus(grace);
    }

    /** Ứng viên cho job no-show: đơn CONFIRMED đã qua giờ bắt đầu; {@link #markNoShow} tự xét tới hạn chưa. */
    @Transactional(readOnly = true)
    public List<Long> findNoShowIds() {
        return bookings.findConfirmedIdsStartedBefore(now());
    }

    /** UC26, FR-NOTI-03: gửi cảnh báo sắp hết giờ cho đơn đang check-in (mốc gửi và chống trùng ở {@link NotificationService#endWarning}). */
    public void warnEndingSoon(Long bookingId) {
        Booking booking = load(bookingId);
        if (booking.getStatus() == CHECKED_IN) {
            notifications.endWarning(booking);
        }
    }

    /** Ứng viên cho job cảnh báo sắp hết giờ: đơn đang check-in chưa tới giờ kết thúc. */
    @Transactional(readOnly = true)
    public List<Long> findEndingSoonIds() {
        return bookings.findCheckedInIdsEndingAfter(now());
    }

    @Transactional(readOnly = true)
    public List<Long> findExpiredHoldIds() {
        return bookings.findHeldIdsExpiredAt(now());
    }

    @Transactional(readOnly = true)
    public List<Long> findOverdueIds() {
        return bookings.findCheckedInIdsEndedBefore(now().minus(properties.overstayGrace()));
    }

    // ------------------------------------------------------------------
    // Hàm phụ
    // ------------------------------------------------------------------

    /** Khách đã đăng nhập dùng hồ sơ tài khoản; khách vãng lai phải nhập thông tin liên hệ (FR-AUTH-01). */
    private Customer customerFor(HoldBookingRequest request, Actor actor) {
        if (actor.isCustomer()) {
            return customers.get(actor.id());
        }
        if (request.contact() == null) {
            throw new DomainException("INVALID_CONTACT", "Khách chưa đăng nhập phải nhập họ tên và số điện thoại");
        }
        return customers.registerOrUpdate(request.contact().toDomain());
    }

    private static void addDeduction(Booking booking, ChargeType type, Money amount, LocalDateTime now) {
        if (amount.isPositive()) {
            booking.getCharges().add(BookingCharge.of(booking, type, amount, booking.getPeriod(), now));
        }
    }

    private static boolean hasDeductions(Booking booking) {
        return booking.getCharges().stream().anyMatch(c -> c.getType().isDeduction());
    }

    private void markBenefitsUsed(Booking booking) {
        vouchers.markUsed(booking);
        memberships.markUsed(booking);
    }

    private void releaseBenefits(Booking booking) {
        vouchers.release(booking);
        memberships.release(booking);
    }

    /** Xác nhận đơn sau khi nhận đủ tiền. */
    private void confirm(Booking booking, Money paid) {
        if (!paid.equals(booking.getTotal())) {
            throw new DomainException("AMOUNT_MISMATCH",
                    "Số tiền thanh toán " + paid.amount() + " khác tổng đơn " + booking.getTotal().amount());
        }
        booking.setPaidAmount(paid.amount());
        changeStatus(booking, CONFIRMED, now());
        markBenefitsUsed(booking);
        bookings.save(booking);
    }

    /** Hủy / đổi khung giờ phải trước giờ bắt đầu ít nhất {@code booking.change-notice}; đúng mốc 2 giờ vẫn được. */
    private void requireBeforeChangeDeadline(Booking booking, LocalDateTime now, String action) {
        LocalDateTime deadline = booking.getPeriod().startAt().minus(properties.changeNotice());
        if (now.isAfter(deadline)) {
            throw new DomainException("CHANGE_DEADLINE_PASSED", "Chỉ được " + action + " trước giờ bắt đầu ít nhất "
                    + properties.changeNotice().toMinutes() + " phút (hạn chót " + deadline + ")");
        }
    }

    /** Đổi trạng thái và ghi một dòng lịch sử. Mọi chỗ đổi trạng thái đơn đều phải gọi hàm này. */
    private static void changeStatus(Booking booking, BookingStatus next, LocalDateTime now) {
        booking.getStatusHistory().add(new BookingStatusHistory(booking, booking.getStatus(), next, now));
        booking.setStatus(next);
    }

    private static void requireStatus(Booking booking, Set<BookingStatus> allowed, String action) {
        if (!allowed.contains(booking.getStatus())) {
            throw new DomainException("INVALID_BOOKING_STATE",
                    "Không thể " + action + " khi đơn đang ở trạng thái " + booking.getStatus());
        }
    }

    private SpaceResource lockResource(Long resourceId) {
        return resources.findByIdForUpdate(resourceId).orElseThrow(() -> new NotFoundException("Vị trí", resourceId));
    }

    /** Chi nhánh chứa vị trí; rỗng nếu vị trí chưa được gán khu vực. */
    private static Branch branchOf(SpaceResource resource) {
        return resource.getZone() == null ? null : resource.getZone().getBranch();
    }

    private Booking load(Long bookingId) {
        return bookings.findById(bookingId).orElseThrow(() -> new NotFoundException("Đơn đặt chỗ", bookingId));
    }

    /** Nhân viên thao tác được mọi đơn (quyền theo vai trò đã kiểm ở controller); khách chỉ đơn của mình. */
    private Booking loadFor(Long bookingId, Actor actor) {
        Booking booking = load(bookingId);
        if (actor.isStaff()) {
            return booking;
        }
        if (actor.isCustomer() && booking.getCustomer().getId().equals(actor.id())) {
            return booking;
        }
        throw new NotFoundException("Đơn đặt chỗ", bookingId);
    }

    private Booking loadByQr(String qrToken) {
        return bookings.findByQrToken(qrToken).orElseThrow(() -> new NotFoundException("Mã QR", "***"));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static String generateCode() {
        StringBuilder sb = new StringBuilder("BK");
        for (int i = 0; i < 8; i++) {
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Nội dung mã QR: ngẫu nhiên 256 bit, không đoán được. */
    private static String generateQrToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
