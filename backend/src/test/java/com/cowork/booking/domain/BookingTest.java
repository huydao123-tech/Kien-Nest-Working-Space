package com.cowork.booking.domain;

import com.cowork.booking.domain.event.BookingEvents.BookingConfirmed;
import com.cowork.booking.domain.event.BookingEvents.BookingHeld;
import com.cowork.booking.domain.event.BookingEvents.BookingRefundRequired;
import com.cowork.shared.domain.DomainException;
import com.cowork.shared.domain.Money;
import com.cowork.shared.domain.TimeRange;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingTest {

    private static final BookingRules RULES = new BookingRules(Duration.ofMinutes(10), Duration.ofMinutes(15), Duration.ofMinutes(5));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 8, 0);
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final Money RENTAL = Money.vnd(100_000);

    @Test
    void holdStartsCountdownAndRaisesEvent() {
        Booking booking = holdAt(9, 11);

        assertThat(booking.status()).isEqualTo(BookingStatus.HELD);
        assertThat(booking.holdExpiresAt()).isEqualTo(NOW.plusMinutes(10));
        assertThat(booking.occupiesSlot()).isTrue();
        assertThat(booking.code()).startsWith("BK").hasSize(10);
        assertThat(booking.pullDomainEvents()).singleElement().isInstanceOf(BookingHeld.class);
    }

    @Test
    void holdRecordsRentalChargeAndInitialStatus() {
        Booking booking = holdAt(9, 11);

        assertThat(booking.charges()).singleElement().satisfies(charge -> {
            assertThat(charge.type()).isEqualTo(ChargeType.RENTAL);
            assertThat(charge.money()).isEqualTo(RENTAL);
            assertThat(charge.startAt()).isEqualTo(NOW.withHour(9));
        });
        assertThat(booking.statusHistory()).containsExactly(new StatusChange(null, BookingStatus.HELD, NOW));
    }

    @Test
    void cannotHoldSlotInThePast() {
        assertThatThrownBy(() -> Booking.hold(UUID.randomUUID(), CUSTOMER_ID,
                new TimeRange(NOW.minusHours(2), NOW.minusHours(1)), 1, RENTAL, RULES, NOW))
                .hasFieldOrPropertyWithValue("code", "START_IN_PAST");
    }

    @Test
    void confirmRequiresExactAmount() {
        Booking booking = holdAt(9, 11);

        assertThatThrownBy(() -> booking.confirmPayment(Money.vnd(1), NOW))
                .hasFieldOrPropertyWithValue("code", "AMOUNT_MISMATCH");

        booking.pullDomainEvents();
        booking.confirmPayment(RENTAL, NOW);
        assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.pullDomainEvents()).singleElement().isInstanceOf(BookingConfirmed.class);
    }

    @Test
    void holdExpiresOnlyAfterDeadline() {
        Booking booking = holdAt(9, 11);

        assertThatThrownBy(() -> booking.expireHold(NOW.plusMinutes(9)))
                .hasFieldOrPropertyWithValue("code", "HOLD_NOT_EXPIRED");

        booking.expireHold(NOW.plusMinutes(10));
        assertThat(booking.status()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(booking.occupiesSlot()).isFalse();
    }

    @Test
    void latePaymentForTakenSlotRequiresRefund() {
        Booking booking = holdAt(9, 11);
        booking.expireHold(NOW.plusMinutes(10));
        booking.pullDomainEvents();

        booking.markRefundPendingAfterLatePayment(RENTAL, NOW.plusMinutes(12));

        assertThat(booking.status()).isEqualTo(BookingStatus.REFUND_PENDING);
        assertThat(booking.pullDomainEvents()).singleElement().isInstanceOf(BookingRefundRequired.class);
    }

    @Test
    void checkInIsAllowedFifteenMinutesEarly() {
        Booking booking = confirmedAt(9, 11);

        assertThatThrownBy(() -> booking.checkIn(RULES, NOW.withHour(8).withMinute(44)))
                .hasFieldOrPropertyWithValue("code", "CHECK_IN_TOO_EARLY");

        booking.checkIn(RULES, NOW.withHour(8).withMinute(45));
        assertThat(booking.status()).isEqualTo(BookingStatus.CHECKED_IN);
    }

    @Test
    void overstayIsChargedOnlyAfterGracePeriod() {
        Booking booking = confirmedAt(9, 11);
        booking.checkIn(RULES, NOW.withHour(9));

        assertThat(booking.overstayPeriod(RULES, NOW.withHour(11).withMinute(5))).isEmpty();
        assertThat(booking.overstayPeriod(RULES, NOW.withHour(11).withMinute(20)))
                .contains(new TimeRange(NOW.withHour(11), NOW.withHour(11).withMinute(20)));
    }

    @Test
    void checkOutWithSurchargeWaitsForSettlement() {
        Booking booking = confirmedAt(9, 11);
        booking.checkIn(RULES, NOW.withHour(9));
        TimeRange overstay = new TimeRange(NOW.withHour(11), NOW.withHour(11).withMinute(20));

        booking.checkOutWithOverstay(overstay, Money.vnd(25_000), NOW.withHour(11).withMinute(20));
        assertThat(booking.status()).isEqualTo(BookingStatus.CHECKED_OUT);
        assertThat(booking.outstanding()).isEqualTo(Money.vnd(25_000));
        assertThat(booking.charges()).extracting(BookingCharge::type)
                .containsExactly(ChargeType.RENTAL, ChargeType.OVERSTAY_SURCHARGE);

        booking.settleOutstanding(Money.vnd(25_000), NOW.withHour(11).withMinute(25));
        assertThat(booking.status()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(booking.outstanding()).isEqualTo(Money.ZERO);
    }

    @Test
    void checkOutOnTimeCompletesImmediately() {
        Booking booking = confirmedAt(9, 11);
        booking.checkIn(RULES, NOW.withHour(9));

        booking.checkOut(NOW.withHour(10).withMinute(55));

        assertThat(booking.status()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(booking.statusHistory()).extracting(StatusChange::toStatus).containsExactly(
                BookingStatus.HELD, BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN, BookingStatus.COMPLETED);
    }

    @Test
    void extendingAnOverstayReturnsToCheckedIn() {
        Booking booking = confirmedAt(9, 11);
        booking.checkIn(RULES, NOW.withHour(9));
        booking.markOverstay(RULES, NOW.withHour(11).withMinute(10));

        booking.extendTo(NOW.withHour(12), Money.vnd(50_000), NOW.withHour(11).withMinute(10));

        assertThat(booking.status()).isEqualTo(BookingStatus.CHECKED_IN);
        assertThat(booking.period().endAt()).isEqualTo(NOW.withHour(12));
        assertThat(booking.outstanding()).isEqualTo(Money.vnd(50_000));
        assertThat(booking.charges()).last().satisfies(charge -> {
            assertThat(charge.type()).isEqualTo(ChargeType.EXTENSION);
            assertThat(charge.startAt()).isEqualTo(NOW.withHour(11));
            assertThat(charge.endAt()).isEqualTo(NOW.withHour(12));
        });
    }

    @Test
    void cancellationRefundDependsOnNotice() {
        CancellationPolicy policy = new CancellationPolicy();

        assertThat(confirmedAt(9, 11, NOW.plusDays(2)).cancel(policy, NOW)).isEqualTo(RENTAL);
        assertThat(confirmedAt(11, 12).cancel(policy, NOW)).isEqualTo(Money.vnd(50_000));
        assertThat(confirmedAt(9, 11).cancel(policy, NOW)).isEqualTo(Money.ZERO);
    }

    @Test
    void cannotCancelAfterCheckIn() {
        Booking booking = confirmedAt(9, 11);
        booking.checkIn(RULES, NOW.withHour(9));

        assertThatThrownBy(() -> booking.cancel(new CancellationPolicy(), NOW.withHour(9)))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_BOOKING_STATE");
    }

    private static Booking holdAt(int startHour, int endHour) {
        return holdOn(NOW, startHour, endHour);
    }

    private static Booking holdOn(LocalDateTime day, int startHour, int endHour) {
        return Booking.hold(UUID.randomUUID(), CUSTOMER_ID,
                new TimeRange(day.withHour(startHour), day.withHour(endHour)), 1, RENTAL, RULES, NOW);
    }

    private static Booking confirmedAt(int startHour, int endHour) {
        return confirmedAt(startHour, endHour, NOW);
    }

    private static Booking confirmedAt(int startHour, int endHour, LocalDateTime day) {
        Booking booking = holdOn(day, startHour, endHour);
        booking.confirmPayment(RENTAL, NOW);
        return booking;
    }
}
