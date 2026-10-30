package com.cowork.business.service;

import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.config.BookingProperties;
import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.BookingCharge;
import com.cowork.dataaccess.entity.BookingStatus;
import com.cowork.dataaccess.entity.ChargeType;
import com.cowork.dataaccess.entity.Customer;
import com.cowork.dataaccess.entity.Money;
import com.cowork.dataaccess.entity.Resource;
import com.cowork.dataaccess.entity.StatusChange;
import com.cowork.dataaccess.entity.TimeRange;
import com.cowork.dataaccess.repository.BookingRepository;
import com.cowork.dataaccess.repository.ResourceRepository;
import com.cowork.dto.BookingDtos.HoldBookingRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
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
 * Toàn bộ nghiệp vụ đơn đặt chỗ — xem State Diagram 3.1 trong docs/diagrams.md.
 * Mỗi hàm public là một chức năng: lấy đơn → kiểm tra quy tắc → đổi dữ liệu → lưu → (nếu cần) nhắn tin cho khách.
 *
 * <p>Chống double-booking (BR-01): mọi thao tác có thể chiếm thêm thời gian của một vị trí
 * (giữ chỗ, gia hạn, kích hoạt lại đơn) đều khóa hàng {@link Resource} trước khi kiểm tra chồng lấn,
 * nên các yêu cầu trên cùng vị trí được xử lý lần lượt.
 */
@Service
@Transactional
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final BookingRepository bookings;
    private final ResourceRepository resources;
    private final ResourceService resourceService;
    private final PricingService pricing;
    private final CustomerService customers;
    private final NotificationService notifications;
    private final Clock clock;
    private final BookingProperties properties;

    public BookingService(BookingRepository bookings, ResourceRepository resources, ResourceService resourceService,
                          PricingService pricing, CustomerService customers, NotificationService notifications,
                          Clock clock, BookingProperties properties) {
        this.bookings = bookings;
        this.resources = resources;
        this.resourceService = resourceService;
        this.pricing = pricing;
        this.customers = customers;
        this.notifications = notifications;
        this.clock = clock;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // Đặt chỗ và thanh toán
    // ------------------------------------------------------------------

    /** UC03: giữ chỗ tạm 10 phút (BR-02) trong lúc khách thanh toán. */
    public Booking hold(HoldBookingRequest request) {
        LocalDateTime now = now();
        if (!request.endAt().isAfter(request.startAt())) {
            throw new DomainException("INVALID_TIME_RANGE", "Giờ kết thúc phải sau giờ bắt đầu");
        }
        if (!request.endAt().isAfter(now) || request.startAt().isBefore(now.minusMinutes(5))) {
            throw new DomainException("START_IN_PAST", "Không thể đặt khung giờ đã qua");
        }
        TimeRange period = new TimeRange(request.startAt(), request.endAt());

        Resource resource = lockResource(request.resourceId());
        resourceService.requireBookable(resource, request.partySize());
        if (bookings.existsOccupying(resource.getId(), period, null)) {
            throw new ConflictException("SLOT_UNAVAILABLE", "Vị trí " + resource.getCode() + " đã có người đặt trong khung giờ này");
        }
        Money rental = pricing.quote(resource.getType(), period).total();
        Customer customer = customers.registerOrUpdate(request.contact().toDomain());

        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCode(generateCode());
        booking.setQrToken(generateQrToken());
        booking.setResourceId(resource.getId());
        booking.setCustomerId(customer.getId());
        booking.setPeriod(period);
        booking.setPartySize(request.partySize());
        booking.setHoldExpiresAt(now.plus(properties.holdDuration()));
        booking.setCreatedAt(now);
        booking.getCharges().add(BookingCharge.of(ChargeType.RENTAL, rental, period, now));
        changeStatus(booking, HELD, now);
        return bookings.save(booking);
    }

    /**
     * UC04, UC25: {@link PaymentService} gọi khi cổng báo thanh toán thành công, kể cả khi webhook về trễ.
     * Đơn HELD đã quá hạn nhưng job chưa chạy vẫn đang giữ chỗ, nên xác nhận lúc đó vẫn an toàn.
     */
    public void onPaymentSucceeded(UUID bookingId, Money paid) {
        Booking booking = load(bookingId);
        BookingStatus status = booking.getStatus();

        if (status == HELD) {
            confirm(booking, paid);
            notifications.bookingConfirmed(booking);
        } else if (status == EXPIRED || status == FAILED) {
            // Webhook về trễ: chỉ xác nhận lại được nếu chưa ai đặt chỗ đó (BR-11).
            lockResource(booking.getResourceId());
            if (bookings.existsOccupying(booking.getResourceId(), booking.getPeriod(), booking.getId())) {
                booking.setPaidAmount(paid.amount());
                changeStatus(booking, REFUND_PENDING, now());
                bookings.save(booking);
                notifications.refundAfterLatePayment(booking, paid);
            } else {
                confirm(booking, paid);
                notifications.bookingConfirmed(booking);
            }
        } else {
            log.info("Bỏ qua thanh toán thành công trùng lặp cho đơn {} ở trạng thái {}", bookingId, status);
        }
    }

    /** {@link PaymentService} gọi khi cổng báo thanh toán thất bại. */
    public void onPaymentFailed(UUID bookingId) {
        Booking booking = load(bookingId);
        if (booking.getStatus() != HELD) {
            log.info("Bỏ qua thanh toán thất bại cho đơn {} ở trạng thái {}", bookingId, booking.getStatus());
            return;
        }
        changeStatus(booking, FAILED, now());
        bookings.save(booking);
        notifications.paymentFailed(booking);
    }

    /**
     * UC06: hủy đơn chưa check-in, trả về số tiền được hoàn.
     * BR-15 (giả định): báo trước ≥ 24h hoàn 100%, 2h–24h hoàn 50%, dưới 2h không hoàn.
     */
    public Money cancel(UUID bookingId) {
        Booking booking = load(bookingId);
        requireStatus(booking, Set.of(CONFIRMED), "hủy");

        LocalDateTime now = now();
        Duration notice = Duration.between(now, booking.getPeriod().startAt());
        Money refund;
        if (notice.compareTo(Duration.ofHours(24)) >= 0) {
            refund = booking.getPaid();
        } else if (notice.compareTo(Duration.ofHours(2)) >= 0) {
            refund = booking.getPaid().percent(50);
        } else {
            refund = Money.ZERO;
        }

        changeStatus(booking, CANCELLED, now);
        bookings.save(booking);
        notifications.bookingCancelled(booking, refund);
        return refund;
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
    public Booking extend(UUID bookingId, LocalDateTime newEndAt) {
        Booking booking = load(bookingId);
        requireStatus(booking, Set.of(CHECKED_IN, OVERSTAY), "gia hạn");
        LocalDateTime oldEndAt = booking.getPeriod().endAt();
        if (newEndAt == null || !newEndAt.isAfter(oldEndAt)) {
            throw new DomainException("INVALID_EXTENSION", "Giờ kết thúc mới phải sau " + oldEndAt);
        }
        TimeRange extension = new TimeRange(oldEndAt, newEndAt);

        Resource resource = lockResource(booking.getResourceId());
        if (bookings.existsOccupying(booking.getResourceId(), extension, booking.getId())) {
            throw new ConflictException("EXTENSION_UNAVAILABLE",
                    "Vị trí đã có khách đặt tiếp, không thể gia hạn tới " + newEndAt);
        }
        Money price = pricing.quote(resource.getType(), extension).total();

        LocalDateTime now = now();
        booking.setPeriod(booking.getPeriod().withEnd(newEndAt));
        booking.getCharges().add(BookingCharge.of(ChargeType.EXTENSION, price, extension, now));
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
            Resource resource = resources.findById(booking.getResourceId())
                    .orElseThrow(() -> new NotFoundException("Vị trí", booking.getResourceId()));
            Money surcharge = pricing.overstaySurcharge(resource.getType(), overstay);
            if (surcharge.isPositive()) {
                booking.getCharges().add(BookingCharge.of(ChargeType.OVERSTAY_SURCHARGE, surcharge, overstay, now));
            }
        }

        booking.setCheckedOutAt(now);
        changeStatus(booking, booking.getOutstanding().isPositive() ? CHECKED_OUT : COMPLETED, now);
        return bookings.save(booking);
    }

    /** UC16: thu nốt khoản phát sinh (gia hạn, phụ phí) tại quầy. */
    public Booking settleOutstanding(UUID bookingId, Money amount) {
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
    // Việc chạy định kỳ (BookingJobs gọi) — UC24
    // ------------------------------------------------------------------

    /** Giải phóng chỗ của đơn giữ chỗ quá hạn. Gọi riêng cho từng đơn để một đơn lỗi không làm hỏng cả lượt quét. */
    public void expireHold(UUID bookingId) {
        Booking booking = load(bookingId);
        requireStatus(booking, Set.of(HELD), "hết hạn giữ chỗ");
        if (now().isBefore(booking.getHoldExpiresAt())) {
            throw new DomainException("HOLD_NOT_EXPIRED", "Đơn vẫn còn trong thời gian giữ chỗ");
        }
        changeStatus(booking, EXPIRED, now());
        bookings.save(booking);
        notifications.holdExpired(booking);
    }

    /** Đánh dấu khách đang ngồi quá giờ kết thúc (sau thời gian ân hạn). */
    public void flagOverstay(UUID bookingId) {
        Booking booking = load(bookingId);
        LocalDateTime now = now();
        boolean overdue = booking.getStatus() == CHECKED_IN
                && now.isAfter(booking.getPeriod().endAt().plus(properties.overstayGrace()));
        if (overdue) {
            changeStatus(booking, OVERSTAY, now);
            bookings.save(booking);
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> findExpiredHoldIds() {
        return bookings.findHeldIdsExpiredAt(now());
    }

    @Transactional(readOnly = true)
    public List<UUID> findOverdueIds() {
        return bookings.findCheckedInIdsEndedBefore(now().minus(properties.overstayGrace()));
    }

    @Transactional(readOnly = true)
    public Booking get(UUID bookingId) {
        return load(bookingId);
    }

    // ------------------------------------------------------------------
    // Hàm phụ
    // ------------------------------------------------------------------

    /** Xác nhận đơn sau khi nhận đủ tiền. */
    private void confirm(Booking booking, Money paid) {
        if (!paid.equals(booking.getTotal())) {
            throw new DomainException("AMOUNT_MISMATCH",
                    "Số tiền thanh toán " + paid.amount() + " khác tổng đơn " + booking.getTotal().amount());
        }
        booking.setPaidAmount(paid.amount());
        changeStatus(booking, CONFIRMED, now());
        bookings.save(booking);
    }

    /** Đổi trạng thái và ghi một dòng lịch sử. Mọi chỗ đổi trạng thái đơn đều phải gọi hàm này. */
    private static void changeStatus(Booking booking, BookingStatus next, LocalDateTime now) {
        booking.getStatusHistory().add(new StatusChange(booking.getStatus(), next, now));
        booking.setStatus(next);
    }

    private static void requireStatus(Booking booking, Set<BookingStatus> allowed, String action) {
        if (!allowed.contains(booking.getStatus())) {
            throw new DomainException("INVALID_BOOKING_STATE",
                    "Không thể " + action + " khi đơn đang ở trạng thái " + booking.getStatus());
        }
    }

    private Resource lockResource(UUID resourceId) {
        return resources.findByIdForUpdate(resourceId).orElseThrow(() -> new NotFoundException("Vị trí", resourceId));
    }

    private Booking load(UUID bookingId) {
        return bookings.findById(bookingId).orElseThrow(() -> new NotFoundException("Đơn đặt chỗ", bookingId));
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
