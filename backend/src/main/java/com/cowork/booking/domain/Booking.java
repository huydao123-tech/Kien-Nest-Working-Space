package com.cowork.booking.domain;

import com.cowork.booking.domain.event.BookingEvents.BookingCancelled;
import com.cowork.booking.domain.event.BookingEvents.BookingCheckedIn;
import com.cowork.booking.domain.event.BookingEvents.BookingCheckedOut;
import com.cowork.booking.domain.event.BookingEvents.BookingConfirmed;
import com.cowork.booking.domain.event.BookingEvents.BookingExtended;
import com.cowork.booking.domain.event.BookingEvents.BookingHeld;
import com.cowork.booking.domain.event.BookingEvents.BookingOverstayed;
import com.cowork.booking.domain.event.BookingEvents.BookingRefundRequired;
import com.cowork.booking.domain.event.BookingEvents.BookingReleased;
import com.cowork.booking.domain.event.BookingEvents.ReleaseReason;
import com.cowork.shared.domain.AggregateRoot;
import com.cowork.shared.domain.DomainException;
import com.cowork.shared.domain.Money;
import com.cowork.shared.domain.TimeRange;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.cowork.booking.domain.BookingStatus.CANCELLED;
import static com.cowork.booking.domain.BookingStatus.CHECKED_IN;
import static com.cowork.booking.domain.BookingStatus.CHECKED_OUT;
import static com.cowork.booking.domain.BookingStatus.COMPLETED;
import static com.cowork.booking.domain.BookingStatus.CONFIRMED;
import static com.cowork.booking.domain.BookingStatus.EXPIRED;
import static com.cowork.booking.domain.BookingStatus.FAILED;
import static com.cowork.booking.domain.BookingStatus.HELD;
import static com.cowork.booking.domain.BookingStatus.OVERSTAY;
import static com.cowork.booking.domain.BookingStatus.REFUND_PENDING;

/**
 * Aggregate root: một khách đặt một vị trí trong một khung giờ.
 * Mọi chuyển trạng thái đi qua các phương thức nghiệp vụ dưới đây; không có setter.
 * Kiểm tra trùng chỗ (BR-01) cần dữ liệu ngoài aggregate nên nằm ở {@code BookingService}.
 *
 * <p>Các khoản phải thu ({@link BookingCharge}) và lịch sử trạng thái ({@link StatusChange}) thuộc
 * aggregate này nhưng lưu ở bảng riêng.
 */
@Entity
@Table(name = "booking")
public class Booking extends AggregateRoot {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 12)
    private String code;

    /** Nội dung mã QR: ngẫu nhiên, không đoán được, tách biệt với mã hiển thị. */
    @Column(nullable = false, unique = true, length = 64)
    private String qrToken;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Embedded
    private TimeRange period;

    private int partySize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    private long paidAmount;

    private LocalDateTime holdExpiresAt;
    private LocalDateTime checkedInAt;
    private LocalDateTime checkedOutAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** Kiểu wrapper để Spring Data nhận biết entity mới (version null) và gọi persist thay vì merge. */
    @Version
    private Long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "booking_charge", joinColumns = @JoinColumn(name = "booking_id"))
    @OrderColumn(name = "line_no")
    private List<BookingCharge> charges = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "booking_status_history", joinColumns = @JoinColumn(name = "booking_id"))
    @OrderColumn(name = "line_no")
    private List<StatusChange> statusHistory = new ArrayList<>();

    protected Booking() {
    }

    /** UC03 bước 7: tạo đơn ở trạng thái giữ chỗ tạm (soft lock). */
    public static Booking hold(UUID resourceId, UUID customerId, TimeRange period, int partySize, Money rental,
                               BookingRules rules, LocalDateTime now) {
        if (partySize < 1) {
            throw new DomainException("INVALID_PARTY_SIZE", "Số người phải lớn hơn 0");
        }
        if (!period.endAt().isAfter(now) || period.startAt().isBefore(now.minusMinutes(5))) {
            throw new DomainException("START_IN_PAST", "Không thể đặt khung giờ đã qua");
        }
        Booking booking = new Booking();
        booking.id = UUID.randomUUID();
        booking.code = generateCode();
        booking.qrToken = generateQrToken();
        booking.resourceId = resourceId;
        booking.customerId = customerId;
        booking.period = period;
        booking.partySize = partySize;
        booking.holdExpiresAt = now.plus(rules.holdDuration());
        booking.createdAt = now;
        booking.charges.add(BookingCharge.of(ChargeType.RENTAL, rental, period, now));
        booking.transitionTo(HELD, now);
        booking.registerEvent(new BookingHeld(booking.id, resourceId, period, booking.holdExpiresAt, now));
        return booking;
    }

    /**
     * Thanh toán thành công khi đơn còn giữ chỗ. Không kiểm tra hạn giữ chỗ:
     * đơn HELD vẫn chiếm vị trí cho tới khi job hết hạn chạy, nên xác nhận lúc này vẫn an toàn.
     */
    public void confirmPayment(Money paid, LocalDateTime now) {
        requireStatus(EnumSet.of(HELD), "xác nhận thanh toán");
        acceptPayment(paid, now);
    }

    /** UC25: webhook về trễ sau khi đơn đã hết hạn nhưng vị trí vẫn trống — kích hoạt lại đơn. */
    public void reactivateAfterLatePayment(Money paid, LocalDateTime now) {
        requireStatus(EnumSet.of(EXPIRED, FAILED), "kích hoạt lại");
        acceptPayment(paid, now);
    }

    /** UC25: webhook về trễ nhưng vị trí đã bị người khác đặt — chuyển chờ hoàn tiền (BR-11). */
    public void markRefundPendingAfterLatePayment(Money paid, LocalDateTime now) {
        requireStatus(EnumSet.of(EXPIRED, FAILED), "chuyển chờ hoàn tiền");
        paidAmount = paid.amount();
        transitionTo(REFUND_PENDING, now);
        registerEvent(new BookingRefundRequired(id, paid, "SLOT_TAKEN_AFTER_LATE_PAYMENT", customerId, now));
    }

    public void expireHold(LocalDateTime now) {
        requireStatus(EnumSet.of(HELD), "hết hạn giữ chỗ");
        if (now.isBefore(holdExpiresAt)) {
            throw new DomainException("HOLD_NOT_EXPIRED", "Đơn vẫn còn trong thời gian giữ chỗ");
        }
        transitionTo(EXPIRED, now);
        registerEvent(new BookingReleased(id, ReleaseReason.HOLD_EXPIRED, customerId, now));
    }

    public void failPayment(LocalDateTime now) {
        requireStatus(EnumSet.of(HELD), "ghi nhận thanh toán thất bại");
        transitionTo(FAILED, now);
        registerEvent(new BookingReleased(id, ReleaseReason.PAYMENT_FAILED, customerId, now));
    }

    /** UC06: hủy đơn chưa check-in, trả về số tiền được hoàn. */
    public Money cancel(CancellationPolicy policy, LocalDateTime now) {
        requireStatus(EnumSet.of(CONFIRMED), "hủy");
        Money refund = policy.refundFor(paid(), period.startAt(), now);
        transitionTo(CANCELLED, now);
        registerEvent(new BookingCancelled(id, refund, customerId, now));
        if (refund.isPositive()) {
            registerEvent(new BookingRefundRequired(id, refund, "CUSTOMER_CANCELLED", customerId, now));
        }
        return refund;
    }

    /** UC07: check-in trong cửa sổ [start - earlyWindow, end). */
    public void checkIn(BookingRules rules, LocalDateTime now) {
        requireStatus(EnumSet.of(CONFIRMED), "check-in");
        if (now.isBefore(period.startAt().minus(rules.checkInEarlyWindow()))) {
            throw new DomainException("CHECK_IN_TOO_EARLY",
                    "Chỉ được check-in từ " + period.startAt().minus(rules.checkInEarlyWindow()));
        }
        if (!now.isBefore(period.endAt())) {
            throw new DomainException("CHECK_IN_TOO_LATE", "Khung giờ đặt chỗ đã kết thúc");
        }
        checkedInAt = now;
        transitionTo(CHECKED_IN, now);
        registerEvent(new BookingCheckedIn(id, resourceId, now));
    }

    /** Khoảng thời gian cần trống và cần tính tiền nếu gia hạn tới {@code newEndAt}. */
    public TimeRange extensionRange(LocalDateTime newEndAt) {
        if (!newEndAt.isAfter(period.endAt())) {
            throw new DomainException("INVALID_EXTENSION", "Giờ kết thúc mới phải sau " + period.endAt());
        }
        return new TimeRange(period.endAt(), newEndAt);
    }

    /** UC08: gia hạn tại chỗ. Tiền gia hạn được cộng vào khoản phải trả khi check-out. */
    public void extendTo(LocalDateTime newEndAt, Money price, LocalDateTime now) {
        requireStatus(EnumSet.of(CHECKED_IN, OVERSTAY), "gia hạn");
        TimeRange extension = extensionRange(newEndAt);
        period = period.withEnd(newEndAt);
        charges.add(BookingCharge.of(ChargeType.EXTENSION, price, extension, now));
        if (status == OVERSTAY && newEndAt.isAfter(now)) {
            transitionTo(CHECKED_IN, now);
        }
        registerEvent(new BookingExtended(id, newEndAt, price, now));
    }

    public boolean isOverdue(BookingRules rules, LocalDateTime now) {
        return status == CHECKED_IN && now.isAfter(period.endAt().plus(rules.overstayGrace()));
    }

    public void markOverstay(BookingRules rules, LocalDateTime now) {
        if (!isOverdue(rules, now)) {
            throw new DomainException("NOT_OVERDUE", "Đơn chưa quá giờ");
        }
        transitionTo(OVERSTAY, now);
        registerEvent(new BookingOverstayed(id, resourceId, now));
    }

    /** Khoảng quá giờ phải tính phụ phí; rỗng nếu còn trong thời gian ân hạn. */
    public Optional<TimeRange> overstayPeriod(BookingRules rules, LocalDateTime checkOutAt) {
        if (!checkOutAt.isAfter(period.endAt().plus(rules.overstayGrace()))) {
            return Optional.empty();
        }
        return Optional.of(new TimeRange(period.endAt(), checkOutAt));
    }

    /** UC09: check-out trong giờ (hoặc trong thời gian ân hạn) — không phát sinh phụ phí. */
    public void checkOut(LocalDateTime now) {
        requireStatus(EnumSet.of(CHECKED_IN, OVERSTAY), "check-out");
        finishCheckOut(now);
    }

    /** UC09: check-out quá giờ — ghi thêm khoản phụ phí cho khoảng quá giờ (BR-06). */
    public void checkOutWithOverstay(TimeRange overstay, Money surcharge, LocalDateTime now) {
        requireStatus(EnumSet.of(CHECKED_IN, OVERSTAY), "check-out");
        if (surcharge.isPositive()) {
            charges.add(BookingCharge.of(ChargeType.OVERSTAY_SURCHARGE, surcharge, overstay, now));
        }
        finishCheckOut(now);
    }

    /** UC16: thu nốt khoản phát sinh tại quầy. */
    public void settleOutstanding(Money amount, LocalDateTime now) {
        requireStatus(EnumSet.of(CHECKED_OUT), "thanh toán khoản phát sinh");
        if (!amount.equals(outstanding())) {
            throw new DomainException("AMOUNT_MISMATCH", "Số tiền cần thu là " + outstanding().amount());
        }
        paidAmount += amount.amount();
        transitionTo(COMPLETED, now);
    }

    public boolean occupiesSlot() {
        return BookingStatus.SLOT_OCCUPYING.contains(status);
    }

    public Money total() {
        return charges.stream().map(BookingCharge::money).reduce(Money.ZERO, Money::plus);
    }

    public Money paid() {
        return Money.vnd(paidAmount);
    }

    public Money outstanding() {
        return total().minus(paid());
    }

    private void finishCheckOut(LocalDateTime now) {
        checkedOutAt = now;
        transitionTo(outstanding().isPositive() ? CHECKED_OUT : COMPLETED, now);
        registerEvent(new BookingCheckedOut(id, resourceId, outstanding(), now));
    }

    private void acceptPayment(Money paid, LocalDateTime now) {
        if (!paid.equals(total())) {
            throw new DomainException("AMOUNT_MISMATCH", "Số tiền thanh toán " + paid.amount() + " khác tổng đơn " + total().amount());
        }
        paidAmount = paid.amount();
        transitionTo(CONFIRMED, now);
        registerEvent(new BookingConfirmed(id, code, qrToken, customerId, period, now));
    }

    private void transitionTo(BookingStatus next, LocalDateTime now) {
        statusHistory.add(new StatusChange(status, next, now));
        status = next;
    }

    private void requireStatus(Set<BookingStatus> allowed, String action) {
        if (!allowed.contains(status)) {
            throw new DomainException("INVALID_BOOKING_STATE", "Không thể " + action + " khi đơn đang ở trạng thái " + status);
        }
    }

    private static String generateCode() {
        StringBuilder sb = new StringBuilder("BK");
        for (int i = 0; i < 8; i++) {
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String generateQrToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public UUID id() {
        return id;
    }

    public String code() {
        return code;
    }

    public String qrToken() {
        return qrToken;
    }

    public UUID customerId() {
        return customerId;
    }

    public UUID resourceId() {
        return resourceId;
    }

    public TimeRange period() {
        return period;
    }

    public int partySize() {
        return partySize;
    }

    public BookingStatus status() {
        return status;
    }

    public LocalDateTime holdExpiresAt() {
        return holdExpiresAt;
    }

    public LocalDateTime checkedInAt() {
        return checkedInAt;
    }

    public LocalDateTime checkedOutAt() {
        return checkedOutAt;
    }

    public List<BookingCharge> charges() {
        return List.copyOf(charges);
    }

    public List<StatusChange> statusHistory() {
        return List.copyOf(statusHistory);
    }
}
