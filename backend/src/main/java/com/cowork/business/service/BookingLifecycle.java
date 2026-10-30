package com.cowork.business.service;

import com.cowork.business.exception.DomainException;
import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.BookingCharge;
import com.cowork.dataaccess.entity.BookingStatus;
import com.cowork.dataaccess.entity.ChargeType;
import com.cowork.dataaccess.entity.Money;
import com.cowork.dataaccess.entity.StatusChange;
import com.cowork.dataaccess.entity.TimeRange;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.cowork.dataaccess.entity.BookingStatus.CANCELLED;
import static com.cowork.dataaccess.entity.BookingStatus.CHECKED_IN;
import static com.cowork.dataaccess.entity.BookingStatus.CHECKED_OUT;
import static com.cowork.dataaccess.entity.BookingStatus.COMPLETED;
import static com.cowork.dataaccess.entity.BookingStatus.CONFIRMED;
import static com.cowork.dataaccess.entity.BookingStatus.EXPIRED;
import static com.cowork.dataaccess.entity.BookingStatus.FAILED;
import static com.cowork.dataaccess.entity.BookingStatus.HELD;
import static com.cowork.dataaccess.entity.BookingStatus.OVERSTAY;
import static com.cowork.dataaccess.entity.BookingStatus.REFUND_PENDING;

/**
 * Quy tắc vòng đời đơn đặt chỗ (tầng nghiệp vụ) — xem State Diagram 3.1 trong docs/diagrams.md.
 *
 * <p>Không đọc / ghi database: nhận một {@link Booking}, kiểm tra quy tắc rồi thay đổi dữ liệu của nó.
 * {@link BookingService} lo phần đọc, khóa, lưu và gửi thông báo. Tách riêng như vậy để
 * kiểm thử quy tắc mà không cần Spring hay database.
 */
public class BookingLifecycle {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final BookingRules rules;
    private final CancellationPolicy cancellationPolicy = new CancellationPolicy();

    public BookingLifecycle(BookingRules rules) {
        this.rules = rules;
    }

    /** Kiểm tra khung giờ người dùng nhập rồi tạo {@link TimeRange}. */
    public static TimeRange period(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new DomainException("INVALID_TIME_RANGE", "Giờ kết thúc phải sau giờ bắt đầu");
        }
        return new TimeRange(startAt, endAt);
    }

    /** UC03 bước 7: tạo đơn ở trạng thái giữ chỗ tạm (soft lock). */
    public Booking hold(UUID resourceId, UUID customerId, TimeRange period, int partySize, Money rental, LocalDateTime now) {
        if (partySize < 1) {
            throw new DomainException("INVALID_PARTY_SIZE", "Số người phải lớn hơn 0");
        }
        if (!period.endAt().isAfter(now) || period.startAt().isBefore(now.minusMinutes(5))) {
            throw new DomainException("START_IN_PAST", "Không thể đặt khung giờ đã qua");
        }
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCode(generateCode());
        booking.setQrToken(generateQrToken());
        booking.setResourceId(resourceId);
        booking.setCustomerId(customerId);
        booking.setPeriod(period);
        booking.setPartySize(partySize);
        booking.setHoldExpiresAt(now.plus(rules.holdDuration()));
        booking.setCreatedAt(now);
        booking.getCharges().add(BookingCharge.of(ChargeType.RENTAL, rental, period, now));
        transition(booking, HELD, now);
        return booking;
    }

    /**
     * Thanh toán thành công khi đơn còn giữ chỗ. Không kiểm tra hạn giữ chỗ:
     * đơn HELD vẫn chiếm vị trí cho tới khi job hết hạn chạy, nên xác nhận lúc này vẫn an toàn.
     */
    public void confirmPayment(Booking booking, Money paid, LocalDateTime now) {
        requireStatus(booking, EnumSet.of(HELD), "xác nhận thanh toán");
        acceptPayment(booking, paid, now);
    }

    /** UC25: webhook về trễ sau khi đơn đã hết hạn nhưng vị trí vẫn trống — kích hoạt lại đơn. */
    public void reactivateAfterLatePayment(Booking booking, Money paid, LocalDateTime now) {
        requireStatus(booking, EnumSet.of(EXPIRED, FAILED), "kích hoạt lại");
        acceptPayment(booking, paid, now);
    }

    /** UC25: webhook về trễ nhưng vị trí đã bị người khác đặt — chuyển chờ hoàn tiền (BR-11). */
    public void markRefundPendingAfterLatePayment(Booking booking, Money paid, LocalDateTime now) {
        requireStatus(booking, EnumSet.of(EXPIRED, FAILED), "chuyển chờ hoàn tiền");
        booking.setPaidAmount(paid.amount());
        transition(booking, REFUND_PENDING, now);
    }

    public void expireHold(Booking booking, LocalDateTime now) {
        requireStatus(booking, EnumSet.of(HELD), "hết hạn giữ chỗ");
        if (now.isBefore(booking.getHoldExpiresAt())) {
            throw new DomainException("HOLD_NOT_EXPIRED", "Đơn vẫn còn trong thời gian giữ chỗ");
        }
        transition(booking, EXPIRED, now);
    }

    public void failPayment(Booking booking, LocalDateTime now) {
        requireStatus(booking, EnumSet.of(HELD), "ghi nhận thanh toán thất bại");
        transition(booking, FAILED, now);
    }

    /** UC06: hủy đơn chưa check-in, trả về số tiền được hoàn. */
    public Money cancel(Booking booking, LocalDateTime now) {
        requireStatus(booking, EnumSet.of(CONFIRMED), "hủy");
        Money refund = cancellationPolicy.refundFor(booking.getPaid(), booking.getPeriod().startAt(), now);
        transition(booking, CANCELLED, now);
        return refund;
    }

    /** UC07: check-in trong cửa sổ [start - earlyWindow, end). */
    public void checkIn(Booking booking, LocalDateTime now) {
        requireStatus(booking, EnumSet.of(CONFIRMED), "check-in");
        LocalDateTime earliest = booking.getPeriod().startAt().minus(rules.checkInEarlyWindow());
        if (now.isBefore(earliest)) {
            throw new DomainException("CHECK_IN_TOO_EARLY", "Chỉ được check-in từ " + earliest);
        }
        if (!now.isBefore(booking.getPeriod().endAt())) {
            throw new DomainException("CHECK_IN_TOO_LATE", "Khung giờ đặt chỗ đã kết thúc");
        }
        booking.setCheckedInAt(now);
        transition(booking, CHECKED_IN, now);
    }

    /** Khoảng thời gian cần trống và cần tính tiền nếu gia hạn tới {@code newEndAt}. */
    public TimeRange extensionRange(Booking booking, LocalDateTime newEndAt) {
        if (newEndAt == null || !newEndAt.isAfter(booking.getPeriod().endAt())) {
            throw new DomainException("INVALID_EXTENSION", "Giờ kết thúc mới phải sau " + booking.getPeriod().endAt());
        }
        return new TimeRange(booking.getPeriod().endAt(), newEndAt);
    }

    /** UC08: gia hạn tại chỗ. Tiền gia hạn được cộng vào khoản phải trả khi check-out. */
    public void extend(Booking booking, LocalDateTime newEndAt, Money price, LocalDateTime now) {
        requireStatus(booking, EnumSet.of(CHECKED_IN, OVERSTAY), "gia hạn");
        TimeRange extension = extensionRange(booking, newEndAt);
        booking.setPeriod(booking.getPeriod().withEnd(newEndAt));
        booking.getCharges().add(BookingCharge.of(ChargeType.EXTENSION, price, extension, now));
        if (booking.getStatus() == OVERSTAY && newEndAt.isAfter(now)) {
            transition(booking, CHECKED_IN, now);
        }
    }

    public boolean isOverdue(Booking booking, LocalDateTime now) {
        return booking.getStatus() == CHECKED_IN && now.isAfter(booking.getPeriod().endAt().plus(rules.overstayGrace()));
    }

    public void markOverstay(Booking booking, LocalDateTime now) {
        if (!isOverdue(booking, now)) {
            throw new DomainException("NOT_OVERDUE", "Đơn chưa quá giờ");
        }
        transition(booking, OVERSTAY, now);
    }

    /** Khoảng quá giờ phải tính phụ phí; rỗng nếu còn trong thời gian ân hạn (BR-06). */
    public Optional<TimeRange> overstayPeriod(Booking booking, LocalDateTime checkOutAt) {
        LocalDateTime end = booking.getPeriod().endAt();
        if (!checkOutAt.isAfter(end.plus(rules.overstayGrace()))) {
            return Optional.empty();
        }
        return Optional.of(new TimeRange(end, checkOutAt));
    }

    /** UC09: check-out trong giờ (hoặc trong thời gian ân hạn) — không phát sinh phụ phí. */
    public void checkOut(Booking booking, LocalDateTime now) {
        requireStatus(booking, EnumSet.of(CHECKED_IN, OVERSTAY), "check-out");
        finishCheckOut(booking, now);
    }

    /** UC09: check-out quá giờ — ghi thêm khoản phụ phí cho khoảng quá giờ (BR-06). */
    public void checkOutWithOverstay(Booking booking, TimeRange overstay, Money surcharge, LocalDateTime now) {
        requireStatus(booking, EnumSet.of(CHECKED_IN, OVERSTAY), "check-out");
        if (surcharge.isPositive()) {
            booking.getCharges().add(BookingCharge.of(ChargeType.OVERSTAY_SURCHARGE, surcharge, overstay, now));
        }
        finishCheckOut(booking, now);
    }

    /** UC16: thu nốt khoản phát sinh tại quầy. */
    public void settleOutstanding(Booking booking, Money amount, LocalDateTime now) {
        requireStatus(booking, EnumSet.of(CHECKED_OUT), "thanh toán khoản phát sinh");
        if (!amount.equals(booking.getOutstanding())) {
            throw new DomainException("AMOUNT_MISMATCH", "Số tiền cần thu là " + booking.getOutstanding().amount());
        }
        booking.setPaidAmount(booking.getPaidAmount() + amount.amount());
        transition(booking, COMPLETED, now);
    }

    private void finishCheckOut(Booking booking, LocalDateTime now) {
        booking.setCheckedOutAt(now);
        transition(booking, booking.getOutstanding().isPositive() ? CHECKED_OUT : COMPLETED, now);
    }

    private void acceptPayment(Booking booking, Money paid, LocalDateTime now) {
        if (!paid.equals(booking.getTotal())) {
            throw new DomainException("AMOUNT_MISMATCH",
                    "Số tiền thanh toán " + paid.amount() + " khác tổng đơn " + booking.getTotal().amount());
        }
        booking.setPaidAmount(paid.amount());
        transition(booking, CONFIRMED, now);
    }

    /** Đổi trạng thái và ghi một dòng lịch sử — mọi chuyển trạng thái đều phải đi qua đây. */
    private static void transition(Booking booking, BookingStatus next, LocalDateTime now) {
        booking.getStatusHistory().add(new StatusChange(booking.getStatus(), next, now));
        booking.setStatus(next);
    }

    private static void requireStatus(Booking booking, Set<BookingStatus> allowed, String action) {
        if (!allowed.contains(booking.getStatus())) {
            throw new DomainException("INVALID_BOOKING_STATE",
                    "Không thể " + action + " khi đơn đang ở trạng thái " + booking.getStatus());
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
}
