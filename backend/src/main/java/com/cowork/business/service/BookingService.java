package com.cowork.business.service;

import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.config.BookingProperties;
import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.BookingStatus;
import com.cowork.dataaccess.entity.Customer;
import com.cowork.dataaccess.entity.Money;
import com.cowork.dataaccess.entity.Resource;
import com.cowork.dataaccess.entity.TimeRange;
import com.cowork.dataaccess.repository.BookingRepository;
import com.cowork.dataaccess.repository.ResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service đặt chỗ: đọc / khóa / lưu dữ liệu, gọi {@link BookingLifecycle} để áp quy tắc,
 * rồi gọi {@link NotificationService} khi cần báo cho khách.
 *
 * <p>Chống double-booking (BR-01): mọi thao tác có thể chiếm thêm thời gian của một vị trí
 * (giữ chỗ, gia hạn, kích hoạt lại đơn) đều khóa bi quan hàng {@link Resource} trước khi kiểm tra
 * chồng lấn, nên các yêu cầu trên cùng vị trí được tuần tự hóa, còn vị trí khác nhau không chặn nhau.
 */
@Service
@Transactional
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookings;
    private final ResourceRepository resources;
    private final ResourceService resourceService;
    private final PricingService pricing;
    private final CustomerService customers;
    private final NotificationService notifications;
    private final Clock clock;
    private final BookingRules rules;
    private final BookingLifecycle lifecycle;

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
        this.rules = properties.toRules();
        this.lifecycle = new BookingLifecycle(rules);
    }

    /** UC03: giữ chỗ tạm trước khi thanh toán. */
    public Booking hold(HoldBookingCommand command) {
        TimeRange period = BookingLifecycle.period(command.startAt(), command.endAt());
        Resource resource = lockResource(command.resourceId());
        resourceService.requireBookable(resource, command.partySize());
        if (bookings.existsOccupying(resource.getId(), period, null)) {
            throw new ConflictException("SLOT_UNAVAILABLE", "Vị trí " + resource.getCode() + " đã có người đặt trong khung giờ này");
        }
        Money rental = pricing.quote(resource.getType(), period).total();
        Customer customer = customers.registerOrUpdate(command.contact());
        Booking booking = lifecycle.hold(resource.getId(), customer.getId(), period, command.partySize(), rental, now());
        return bookings.save(booking);
    }

    /**
     * UC04, UC25: được {@link PaymentService} gọi khi cổng báo thanh toán thành công, kể cả khi webhook về trễ.
     * Chạy chung giao dịch với việc lưu kết quả thanh toán.
     */
    public void onPaymentSucceeded(UUID bookingId, Money paid) {
        Booking booking = load(bookingId);
        BookingStatus status = booking.getStatus();
        if (status == BookingStatus.HELD) {
            lifecycle.confirmPayment(booking, paid, now());
            bookings.save(booking);
            notifications.bookingConfirmed(booking);
        } else if (status == BookingStatus.EXPIRED || status == BookingStatus.FAILED) {
            lockResource(booking.getResourceId());
            if (bookings.existsOccupying(booking.getResourceId(), booking.getPeriod(), booking.getId())) {
                lifecycle.markRefundPendingAfterLatePayment(booking, paid, now());
                bookings.save(booking);
                notifications.refundAfterLatePayment(booking, paid);
            } else {
                lifecycle.reactivateAfterLatePayment(booking, paid, now());
                bookings.save(booking);
                notifications.bookingConfirmed(booking);
            }
        } else {
            log.info("Bỏ qua thanh toán thành công trùng lặp cho đơn {} ở trạng thái {}", bookingId, status);
        }
    }

    /** Được {@link PaymentService} gọi khi cổng báo thanh toán thất bại. */
    public void onPaymentFailed(UUID bookingId) {
        Booking booking = load(bookingId);
        if (booking.getStatus() != BookingStatus.HELD) {
            log.info("Bỏ qua thanh toán thất bại cho đơn {} ở trạng thái {}", bookingId, booking.getStatus());
            return;
        }
        lifecycle.failPayment(booking, now());
        bookings.save(booking);
        notifications.paymentFailed(booking);
    }

    /** UC06. */
    public Money cancel(UUID bookingId) {
        Booking booking = load(bookingId);
        Money refund = lifecycle.cancel(booking, now());
        bookings.save(booking);
        notifications.bookingCancelled(booking, refund);
        return refund;
    }

    /** UC07. */
    public Booking checkIn(String qrToken) {
        Booking booking = loadByQr(qrToken);
        lifecycle.checkIn(booking, now());
        return bookings.save(booking);
    }

    /** UC08: chỉ gia hạn được nếu vị trí trống trong khoảng gia hạn (BR-07). */
    public Booking extend(UUID bookingId, LocalDateTime newEndAt) {
        Booking booking = load(bookingId);
        TimeRange extension = lifecycle.extensionRange(booking, newEndAt);
        Resource resource = lockResource(booking.getResourceId());
        if (bookings.existsOccupying(booking.getResourceId(), extension, booking.getId())) {
            throw new ConflictException("EXTENSION_UNAVAILABLE",
                    "Vị trí đã có khách đặt tiếp, không thể gia hạn tới " + newEndAt);
        }
        Money price = pricing.quote(resource.getType(), extension).total();
        lifecycle.extend(booking, newEndAt, price, now());
        return bookings.save(booking);
    }

    /** UC09: tính phụ phí quá giờ theo block (BR-06). */
    public Booking checkOut(String qrToken) {
        Booking booking = loadByQr(qrToken);
        LocalDateTime now = now();
        Optional<TimeRange> overstay = lifecycle.overstayPeriod(booking, now);
        if (overstay.isPresent()) {
            Resource resource = resources.findById(booking.getResourceId())
                    .orElseThrow(() -> new NotFoundException("Vị trí", booking.getResourceId()));
            Money surcharge = pricing.overstaySurcharge(resource.getType(), overstay.get());
            lifecycle.checkOutWithOverstay(booking, overstay.get(), surcharge, now);
        } else {
            lifecycle.checkOut(booking, now);
        }
        return bookings.save(booking);
    }

    /** UC16. */
    public Booking settleOutstanding(UUID bookingId, Money amount) {
        Booking booking = load(bookingId);
        lifecycle.settleOutstanding(booking, amount, now());
        return bookings.save(booking);
    }

    /** UC24: gọi riêng cho từng đơn để một đơn lỗi không làm hỏng cả lượt quét. */
    public void expireHold(UUID bookingId) {
        Booking booking = load(bookingId);
        lifecycle.expireHold(booking, now());
        bookings.save(booking);
        notifications.holdExpired(booking);
    }

    public void flagOverstay(UUID bookingId) {
        Booking booking = load(bookingId);
        if (lifecycle.isOverdue(booking, now())) {
            lifecycle.markOverstay(booking, now());
            bookings.save(booking);
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> findExpiredHoldIds() {
        return bookings.findHeldIdsExpiredAt(now());
    }

    @Transactional(readOnly = true)
    public List<UUID> findOverdueIds() {
        return bookings.findCheckedInIdsEndedBefore(now().minus(rules.overstayGrace()));
    }

    @Transactional(readOnly = true)
    public Booking get(UUID bookingId) {
        return load(bookingId);
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
}
