package com.cowork.business.service;

import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.DomainException;
import com.cowork.config.BookingProperties;
import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.BookingCharge;
import com.cowork.dataaccess.entity.BookingStatus;
import com.cowork.dataaccess.entity.ChargeType;
import com.cowork.dataaccess.entity.Customer;
import com.cowork.dataaccess.entity.Money;
import com.cowork.dataaccess.entity.Resource;
import com.cowork.dataaccess.entity.ResourceType;
import com.cowork.dataaccess.entity.StatusChange;
import com.cowork.dataaccess.entity.TimeRange;
import com.cowork.dataaccess.repository.BookingRepository;
import com.cowork.dataaccess.repository.ResourceRepository;
import com.cowork.dto.BookingDtos.ContactRequest;
import com.cowork.dto.BookingDtos.HoldBookingRequest;
import com.cowork.dto.PriceQuote;
import com.cowork.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Quy tắc vòng đời đơn đặt chỗ. Repository và các service khác được giả lập bằng Mockito,
 * nên test chạy nhanh, không cần Spring hay database.
 */
class BookingServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 8, 0); // thứ Hai
    private static final Money RENTAL = Money.vnd(100_000);

    private final MutableClock clock = new MutableClock(ZoneId.of("Asia/Ho_Chi_Minh"));
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final ResourceRepository resources = mock(ResourceRepository.class);
    private final ResourceService resourceService = mock(ResourceService.class);
    private final PricingService pricing = mock(PricingService.class);
    private final CustomerService customers = mock(CustomerService.class);
    private final NotificationService notifications = mock(NotificationService.class);

    private final Resource desk = new Resource("HD-01", "Hot Desk 1", ResourceType.HOT_DESK, 1);
    private BookingService service;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        service = new BookingService(bookings, resources, resourceService, pricing, customers, notifications, clock,
                new BookingProperties(Duration.ofMinutes(10), Duration.ofMinutes(15), Duration.ofMinutes(5)));

        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        when(customers.registerOrUpdate(any())).thenReturn(customer);
        when(resources.findByIdForUpdate(desk.getId())).thenReturn(Optional.of(desk));
        when(resources.findById(desk.getId())).thenReturn(Optional.of(desk));
        when(pricing.quote(eq(ResourceType.HOT_DESK), any())).thenReturn(new PriceQuote(4, 30, RENTAL));
        when(bookings.save(any(Booking.class))).thenAnswer(call -> call.getArgument(0));
    }

    // ---------- Giữ chỗ ----------

    @Test
    void holdStartsTenMinuteCountdown() {
        Booking booking = holdAt(9, 11);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.HELD);
        assertThat(booking.getHoldExpiresAt()).isEqualTo(NOW.plusMinutes(10));
        assertThat(booking.getCode()).startsWith("BK").hasSize(10);
        assertThat(booking.getQrToken()).isNotBlank();
    }

    @Test
    void holdRecordsRentalChargeAndInitialStatus() {
        Booking booking = holdAt(9, 11);

        assertThat(booking.getCharges()).singleElement().satisfies(charge -> {
            assertThat(charge.type()).isEqualTo(ChargeType.RENTAL);
            assertThat(charge.money()).isEqualTo(RENTAL);
            assertThat(charge.startAt()).isEqualTo(NOW.withHour(9));
        });
        assertThat(booking.getStatusHistory()).containsExactly(new StatusChange(null, BookingStatus.HELD, NOW));
    }

    @Test
    void cannotHoldSlotInThePast() {
        assertThatThrownBy(() -> service.hold(request(NOW.minusHours(2), NOW.minusHours(1))))
                .hasFieldOrPropertyWithValue("code", "START_IN_PAST");
    }

    @Test
    void rejectsEndBeforeStart() {
        assertThatThrownBy(() -> service.hold(request(NOW.withHour(11), NOW.withHour(9))))
                .hasFieldOrPropertyWithValue("code", "INVALID_TIME_RANGE");
    }

    @Test
    void rejectsSlotThatIsAlreadyTaken() {
        when(bookings.existsOccupying(any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.hold(request(NOW.withHour(9), NOW.withHour(11))))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "SLOT_UNAVAILABLE");
    }

    // ---------- Thanh toán ----------

    @Test
    void paymentConfirmsBookingOnlyWithExactAmount() {
        Booking booking = holdAt(9, 11);

        assertThatThrownBy(() -> service.onPaymentSucceeded(booking.getId(), Money.vnd(1)))
                .hasFieldOrPropertyWithValue("code", "AMOUNT_MISMATCH");

        service.onPaymentSucceeded(booking.getId(), RENTAL);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getPaid()).isEqualTo(RENTAL);
        verify(notifications).bookingConfirmed(booking);
    }

    @Test
    void holdExpiresOnlyAfterDeadline() {
        Booking booking = holdAt(9, 11);

        clock.set(NOW.plusMinutes(9));
        assertThatThrownBy(() -> service.expireHold(booking.getId()))
                .hasFieldOrPropertyWithValue("code", "HOLD_NOT_EXPIRED");

        clock.set(NOW.plusMinutes(10));
        service.expireHold(booking.getId());
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        verify(notifications).holdExpired(booking);
    }

    @Test
    void latePaymentForTakenSlotGoesToRefund() {
        Booking booking = holdAt(9, 11);
        clock.set(NOW.plusMinutes(10));
        service.expireHold(booking.getId());

        when(bookings.existsOccupying(any(), any(), any())).thenReturn(true);
        service.onPaymentSucceeded(booking.getId(), RENTAL);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.REFUND_PENDING);
        verify(notifications).refundAfterLatePayment(booking, RENTAL);
    }

    @Test
    void latePaymentWhileSlotIsFreeReactivatesBooking() {
        Booking booking = holdAt(9, 11);
        clock.set(NOW.plusMinutes(10));
        service.expireHold(booking.getId());

        service.onPaymentSucceeded(booking.getId(), RENTAL);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    // ---------- Check-in, gia hạn, check-out ----------

    @Test
    void checkInIsAllowedFifteenMinutesEarly() {
        Booking booking = confirmedAt(9, 11);

        clock.set(NOW.withMinute(44));
        assertThatThrownBy(() -> service.checkIn(booking.getQrToken()))
                .hasFieldOrPropertyWithValue("code", "CHECK_IN_TOO_EARLY");

        clock.set(NOW.withMinute(45));
        service.checkIn(booking.getQrToken());
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CHECKED_IN);
    }

    @Test
    void checkOutWithinGracePeriodHasNoSurcharge() {
        Booking booking = checkedInAt(9, 11);

        clock.set(NOW.withHour(11).withMinute(5));
        service.checkOut(booking.getQrToken());

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        verify(pricing, never()).overstaySurcharge(any(), any());
        assertThat(booking.getStatusHistory()).extracting(StatusChange::toStatus).containsExactly(
                BookingStatus.HELD, BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN, BookingStatus.COMPLETED);
    }

    @Test
    void lateCheckOutAddsSurchargeAndWaitsForSettlement() {
        Booking booking = checkedInAt(9, 11);
        TimeRange overstay = new TimeRange(NOW.withHour(11), NOW.withHour(11).withMinute(20));
        when(pricing.overstaySurcharge(ResourceType.HOT_DESK, overstay)).thenReturn(Money.vnd(25_000));

        clock.set(NOW.withHour(11).withMinute(20));
        service.checkOut(booking.getQrToken());
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CHECKED_OUT);
        assertThat(booking.getOutstanding()).isEqualTo(Money.vnd(25_000));
        assertThat(booking.getCharges()).extracting(BookingCharge::type)
                .containsExactly(ChargeType.RENTAL, ChargeType.OVERSTAY_SURCHARGE);

        service.settleOutstanding(booking.getId(), Money.vnd(25_000));
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(booking.getOutstanding()).isEqualTo(Money.ZERO);
    }

    @Test
    void extendingAnOverstayReturnsToCheckedIn() {
        Booking booking = checkedInAt(9, 11);
        clock.set(NOW.withHour(11).withMinute(10));
        service.flagOverstay(booking.getId());
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.OVERSTAY);

        TimeRange extension = new TimeRange(NOW.withHour(11), NOW.withHour(12));
        when(pricing.quote(ResourceType.HOT_DESK, extension)).thenReturn(new PriceQuote(2, 30, Money.vnd(50_000)));
        service.extend(booking.getId(), NOW.withHour(12));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CHECKED_IN);
        assertThat(booking.getPeriod().endAt()).isEqualTo(NOW.withHour(12));
        assertThat(booking.getOutstanding()).isEqualTo(Money.vnd(50_000));
    }

    // ---------- Hủy ----------

    @Test
    void cancellationRefundDependsOnNotice() {
        assertThat(service.cancel(confirmedOn(NOW.plusDays(2), 9, 11).getId())).isEqualTo(RENTAL);
        assertThat(service.cancel(confirmedAt(11, 12).getId())).isEqualTo(Money.vnd(50_000));
        assertThat(service.cancel(confirmedAt(9, 11).getId())).isEqualTo(Money.ZERO);
    }

    @Test
    void cannotCancelAfterCheckIn() {
        Booking booking = checkedInAt(9, 11);

        assertThatThrownBy(() -> service.cancel(booking.getId()))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_BOOKING_STATE");
    }

    // ---------- Hàm phụ dựng dữ liệu ----------

    private HoldBookingRequest request(LocalDateTime startAt, LocalDateTime endAt) {
        return new HoldBookingRequest(desk.getId(), startAt, endAt, 1, new ContactRequest("Nguyễn Văn A", "0912345678", null));
    }

    /** Giữ chỗ rồi dặn repository giả trả lại đúng đơn này khi được hỏi theo id hoặc mã QR. */
    private Booking holdOn(LocalDateTime day, int startHour, int endHour) {
        Booking booking = service.hold(request(day.withHour(startHour), day.withHour(endHour)));
        when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(bookings.findByQrToken(booking.getQrToken())).thenReturn(Optional.of(booking));
        return booking;
    }

    private Booking holdAt(int startHour, int endHour) {
        return holdOn(NOW, startHour, endHour);
    }

    private Booking confirmedOn(LocalDateTime day, int startHour, int endHour) {
        Booking booking = holdOn(day, startHour, endHour);
        service.onPaymentSucceeded(booking.getId(), RENTAL);
        return booking;
    }

    private Booking confirmedAt(int startHour, int endHour) {
        return confirmedOn(NOW, startHour, endHour);
    }

    private Booking checkedInAt(int startHour, int endHour) {
        Booking booking = confirmedAt(startHour, endHour);
        clock.set(NOW.withHour(startHour));
        service.checkIn(booking.getQrToken());
        return booking;
    }
}
