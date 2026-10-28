package com.cowork.booking.application;

import com.cowork.booking.domain.Booking;
import com.cowork.booking.domain.BookingRepository;
import com.cowork.booking.domain.BookingRules;
import com.cowork.booking.domain.BookingStatus;
import com.cowork.booking.domain.CancellationPolicy;
import com.cowork.customer.application.CustomerService;
import com.cowork.customer.domain.Customer;
import com.cowork.pricing.application.PricingService;
import com.cowork.shared.application.DomainEventPublisher;
import com.cowork.shared.domain.ConflictException;
import com.cowork.shared.domain.Money;
import com.cowork.shared.domain.NotFoundException;
import com.cowork.shared.domain.TimeRange;
import com.cowork.space.domain.Resource;
import com.cowork.space.domain.ResourceRepository;
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
 * Application service của context Booking: điều phối aggregate, repository và các context khác.
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
    private final PricingService pricing;
    private final CustomerService customers;
    private final DomainEventPublisher events;
    private final Clock clock;
    private final BookingRules rules;
    private final CancellationPolicy cancellationPolicy = new CancellationPolicy();

    public BookingService(BookingRepository bookings, ResourceRepository resources, PricingService pricing,
                          CustomerService customers, DomainEventPublisher events, Clock clock,
                          BookingProperties properties) {
        this.bookings = bookings;
        this.resources = resources;
        this.pricing = pricing;
        this.customers = customers;
        this.events = events;
        this.clock = clock;
        this.rules = properties.toRules();
    }

    /** UC03: giữ chỗ tạm trước khi thanh toán. */
    public Booking hold(HoldBookingCommand command) {
        TimeRange period = new TimeRange(command.startAt(), command.endAt());
        Resource resource = lockResource(command.resourceId());
        resource.ensureCanHost(command.partySize());
        if (bookings.existsOccupying(resource.id(), period, null)) {
            throw new ConflictException("SLOT_UNAVAILABLE", "Vị trí " + resource.code() + " đã có người đặt trong khung giờ này");
        }
        Money rental = pricing.quote(resource.type(), period).total();
        Customer customer = customers.registerOrUpdate(command.contact());
        Booking booking = Booking.hold(resource.id(), customer.id(), period, command.partySize(), rental, rules, now());
        return saveAndPublish(booking);
    }

    /** Phản ứng với thanh toán thành công, kể cả webhook về trễ (UC04, UC25). Idempotent. */
    public void onPaymentSucceeded(UUID bookingId, Money paid) {
        Booking booking = load(bookingId);
        switch (booking.status()) {
            case HELD -> booking.confirmPayment(paid, now());
            case EXPIRED, FAILED -> {
                lockResource(booking.resourceId());
                if (bookings.existsOccupying(booking.resourceId(), booking.period(), booking.id())) {
                    booking.markRefundPendingAfterLatePayment(paid, now());
                } else {
                    booking.reactivateAfterLatePayment(paid, now());
                }
            }
            default -> {
                log.info("Bỏ qua thanh toán thành công trùng lặp cho đơn {} ở trạng thái {}", bookingId, booking.status());
                return;
            }
        }
        saveAndPublish(booking);
    }

    public void onPaymentFailed(UUID bookingId) {
        Booking booking = load(bookingId);
        if (booking.status() != BookingStatus.HELD) {
            log.info("Bỏ qua thanh toán thất bại cho đơn {} ở trạng thái {}", bookingId, booking.status());
            return;
        }
        booking.failPayment(now());
        saveAndPublish(booking);
    }

    /** UC06. */
    public Money cancel(UUID bookingId) {
        Booking booking = load(bookingId);
        Money refund = booking.cancel(cancellationPolicy, now());
        saveAndPublish(booking);
        return refund;
    }

    /** UC07. */
    public Booking checkIn(String qrToken) {
        Booking booking = loadByQr(qrToken);
        booking.checkIn(rules, now());
        return saveAndPublish(booking);
    }

    /** UC08: chỉ gia hạn được nếu vị trí trống trong khoảng gia hạn (BR-07). */
    public Booking extend(UUID bookingId, LocalDateTime newEndAt) {
        Booking booking = load(bookingId);
        TimeRange extension = booking.extensionRange(newEndAt);
        Resource resource = lockResource(booking.resourceId());
        if (bookings.existsOccupying(booking.resourceId(), extension, booking.id())) {
            throw new ConflictException("EXTENSION_UNAVAILABLE",
                    "Vị trí đã có khách đặt tiếp, không thể gia hạn tới " + newEndAt);
        }
        Money price = pricing.quote(resource.type(), extension).total();
        booking.extendTo(newEndAt, price, now());
        return saveAndPublish(booking);
    }

    /** UC09: tính phụ phí quá giờ theo block (BR-06). */
    public Booking checkOut(String qrToken) {
        Booking booking = loadByQr(qrToken);
        LocalDateTime now = now();
        Optional<TimeRange> overstay = booking.overstayPeriod(rules, now);
        if (overstay.isPresent()) {
            Resource resource = resources.findById(booking.resourceId())
                    .orElseThrow(() -> new NotFoundException("Vị trí", booking.resourceId()));
            Money surcharge = pricing.overstaySurcharge(resource.type(), overstay.get());
            booking.checkOutWithOverstay(overstay.get(), surcharge, now);
        } else {
            booking.checkOut(now);
        }
        return saveAndPublish(booking);
    }

    /** UC16. */
    public Booking settleOutstanding(UUID bookingId, Money amount) {
        Booking booking = load(bookingId);
        booking.settleOutstanding(amount, now());
        return saveAndPublish(booking);
    }

    /** UC24: gọi riêng cho từng đơn để một đơn lỗi không làm hỏng cả lượt quét. */
    public void expireHold(UUID bookingId) {
        Booking booking = load(bookingId);
        booking.expireHold(now());
        saveAndPublish(booking);
    }

    public void flagOverstay(UUID bookingId) {
        Booking booking = load(bookingId);
        if (booking.isOverdue(rules, now())) {
            booking.markOverstay(rules, now());
            saveAndPublish(booking);
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

    private Booking saveAndPublish(Booking booking) {
        Booking saved = bookings.save(booking);
        events.publishEventsOf(booking);
        return saved;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
