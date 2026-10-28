package com.cowork.booking.domain.event;

import com.cowork.shared.domain.DomainEvent;
import com.cowork.shared.domain.Money;
import com.cowork.shared.domain.TimeRange;

import java.time.LocalDateTime;
import java.util.UUID;

/** Các domain event công bố ra ngoài của context Booking (published language). */
public final class BookingEvents {

    private BookingEvents() {
    }

    public record BookingHeld(UUID bookingId, UUID resourceId, TimeRange period, LocalDateTime holdExpiresAt,
                              LocalDateTime occurredAt) implements DomainEvent {
    }

    public record BookingConfirmed(UUID bookingId, String code, String qrToken, UUID customerId, TimeRange period,
                                   LocalDateTime occurredAt) implements DomainEvent {
    }

    public enum ReleaseReason { HOLD_EXPIRED, PAYMENT_FAILED }

    public record BookingReleased(UUID bookingId, ReleaseReason reason, UUID customerId,
                                  LocalDateTime occurredAt) implements DomainEvent {
    }

    public record BookingCancelled(UUID bookingId, Money refund, UUID customerId,
                                   LocalDateTime occurredAt) implements DomainEvent {
    }

    public record BookingRefundRequired(UUID bookingId, Money amount, String reason, UUID customerId,
                                        LocalDateTime occurredAt) implements DomainEvent {
    }

    public record BookingCheckedIn(UUID bookingId, UUID resourceId, LocalDateTime occurredAt) implements DomainEvent {
    }

    public record BookingExtended(UUID bookingId, LocalDateTime newEndAt, Money price,
                                  LocalDateTime occurredAt) implements DomainEvent {
    }

    public record BookingOverstayed(UUID bookingId, UUID resourceId, LocalDateTime occurredAt) implements DomainEvent {
    }

    public record BookingCheckedOut(UUID bookingId, UUID resourceId, Money outstanding,
                                    LocalDateTime occurredAt) implements DomainEvent {
    }
}
