package com.cowork.business.service;

import com.cowork.business.exception.DomainException;
import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.BookingCharge;
import com.cowork.dataaccess.entity.BookingStatus;
import com.cowork.dataaccess.entity.ChargeType;
import com.cowork.dataaccess.entity.Money;
import com.cowork.dataaccess.entity.StatusChange;
import com.cowork.dataaccess.entity.TimeRange;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Quy tắc vòng đời đơn đặt chỗ — chạy không cần Spring hay database. */
class BookingLifecycleTest {

    private static final BookingRules RULES = new BookingRules(Duration.ofMinutes(10), Duration.ofMinutes(15), Duration.ofMinutes(5));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 8, 0);
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final Money RENTAL = Money.vnd(100_000);

    private final BookingLifecycle lifecycle = new BookingLifecycle(RULES);

    @Test
    void holdStartsCountdown() {
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
        assertThatThrownBy(() -> lifecycle.hold(UUID.randomUUID(), CUSTOMER_ID,
                new TimeRange(NOW.minusHours(2), NOW.minusHours(1)), 1, RENTAL, NOW))
                .hasFieldOrPropertyWithValue("code", "START_IN_PAST");
    }

    @Test
    void rejectsEndBeforeStart() {
        assertThatThrownBy(() -> BookingLifecycle.period(NOW.withHour(11), NOW.withHour(9)))
                .hasFieldOrPropertyWithValue("code", "INVALID_TIME_RANGE");
    }

    @Test
    void confirmRequiresExactAmount() {
        Booking booking = holdAt(9, 11);

        assertThatThrownBy(() -> lifecycle.confirmPayment(booking, Money.vnd(1), NOW))
                .hasFieldOrPropertyWithValue("code", "AMOUNT_MISMATCH");

        lifecycle.confirmPayment(booking, RENTAL, NOW);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getPaid()).isEqualTo(RENTAL);
    }

    @Test
    void holdExpiresOnlyAfterDeadline() {
        Booking booking = holdAt(9, 11);

        assertThatThrownBy(() -> lifecycle.expireHold(booking, NOW.plusMinutes(9)))
                .hasFieldOrPropertyWithValue("code", "HOLD_NOT_EXPIRED");

        lifecycle.expireHold(booking, NOW.plusMinutes(10));
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(BookingStatus.SLOT_OCCUPYING).doesNotContain(booking.getStatus());
    }

    @Test
    void latePaymentForTakenSlotRequiresRefund() {
        Booking booking = holdAt(9, 11);
        lifecycle.expireHold(booking, NOW.plusMinutes(10));

        lifecycle.markRefundPendingAfterLatePayment(booking, RENTAL, NOW.plusMinutes(12));
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.REFUND_PENDING);
        assertThat(booking.getPaid()).isEqualTo(RENTAL);
    }

    @Test
    void checkInIsAllowedFifteenMinutesEarly() {
        Booking booking = confirmedAt(9, 11);

        assertThatThrownBy(() -> lifecycle.checkIn(booking, NOW.withHour(8).withMinute(44)))
                .hasFieldOrPropertyWithValue("code", "CHECK_IN_TOO_EARLY");

        lifecycle.checkIn(booking, NOW.withHour(8).withMinute(45));
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CHECKED_IN);
    }

    @Test
    void overstayIsChargedOnlyAfterGracePeriod() {
        Booking booking = confirmedAt(9, 11);
        lifecycle.checkIn(booking, NOW.withHour(9));

        assertThat(lifecycle.overstayPeriod(booking, NOW.withHour(11).withMinute(5))).isEmpty();
        assertThat(lifecycle.overstayPeriod(booking, NOW.withHour(11).withMinute(20)))
                .contains(new TimeRange(NOW.withHour(11), NOW.withHour(11).withMinute(20)));
    }

    @Test
    void checkOutWithSurchargeWaitsForSettlement() {
        Booking booking = confirmedAt(9, 11);
        lifecycle.checkIn(booking, NOW.withHour(9));
        TimeRange overstay = new TimeRange(NOW.withHour(11), NOW.withHour(11).withMinute(20));

        lifecycle.checkOutWithOverstay(booking, overstay, Money.vnd(25_000), NOW.withHour(11).withMinute(20));
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CHECKED_OUT);
        assertThat(booking.getOutstanding()).isEqualTo(Money.vnd(25_000));
        assertThat(booking.getCharges()).extracting(BookingCharge::type)
                .containsExactly(ChargeType.RENTAL, ChargeType.OVERSTAY_SURCHARGE);

        lifecycle.settleOutstanding(booking, Money.vnd(25_000), NOW.withHour(11).withMinute(25));
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(booking.getOutstanding()).isEqualTo(Money.ZERO);
    }

    @Test
    void checkOutOnTimeCompletesImmediately() {
        Booking booking = confirmedAt(9, 11);
        lifecycle.checkIn(booking, NOW.withHour(9));

        lifecycle.checkOut(booking, NOW.withHour(10).withMinute(55));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(booking.getStatusHistory()).extracting(StatusChange::toStatus).containsExactly(
                BookingStatus.HELD, BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN, BookingStatus.COMPLETED);
    }

    @Test
    void extendingAnOverstayReturnsToCheckedIn() {
        Booking booking = confirmedAt(9, 11);
        lifecycle.checkIn(booking, NOW.withHour(9));
        lifecycle.markOverstay(booking, NOW.withHour(11).withMinute(10));

        lifecycle.extend(booking, NOW.withHour(12), Money.vnd(50_000), NOW.withHour(11).withMinute(10));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CHECKED_IN);
        assertThat(booking.getPeriod().endAt()).isEqualTo(NOW.withHour(12));
        assertThat(booking.getOutstanding()).isEqualTo(Money.vnd(50_000));
        assertThat(booking.getCharges()).last().satisfies(charge -> {
            assertThat(charge.type()).isEqualTo(ChargeType.EXTENSION);
            assertThat(charge.startAt()).isEqualTo(NOW.withHour(11));
            assertThat(charge.endAt()).isEqualTo(NOW.withHour(12));
        });
    }

    @Test
    void cancellationRefundDependsOnNotice() {
        assertThat(lifecycle.cancel(confirmedAt(9, 11, NOW.plusDays(2)), NOW)).isEqualTo(RENTAL);
        assertThat(lifecycle.cancel(confirmedAt(11, 12), NOW)).isEqualTo(Money.vnd(50_000));
        assertThat(lifecycle.cancel(confirmedAt(9, 11), NOW)).isEqualTo(Money.ZERO);
    }

    @Test
    void cannotCancelAfterCheckIn() {
        Booking booking = confirmedAt(9, 11);
        lifecycle.checkIn(booking, NOW.withHour(9));

        assertThatThrownBy(() -> lifecycle.cancel(booking, NOW.withHour(9)))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_BOOKING_STATE");
    }

    private Booking holdAt(int startHour, int endHour) {
        return holdOn(NOW, startHour, endHour);
    }

    private Booking holdOn(LocalDateTime day, int startHour, int endHour) {
        return lifecycle.hold(UUID.randomUUID(), CUSTOMER_ID,
                new TimeRange(day.withHour(startHour), day.withHour(endHour)), 1, RENTAL, NOW);
    }

    private Booking confirmedAt(int startHour, int endHour) {
        return confirmedAt(startHour, endHour, NOW);
    }

    private Booking confirmedAt(int startHour, int endHour, LocalDateTime day) {
        Booking booking = holdOn(day, startHour, endHour);
        lifecycle.confirmPayment(booking, RENTAL, NOW);
        return booking;
    }
}
