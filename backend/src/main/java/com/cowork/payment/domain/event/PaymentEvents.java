package com.cowork.payment.domain.event;

import com.cowork.shared.domain.DomainEvent;
import com.cowork.shared.domain.Money;

import java.time.LocalDateTime;
import java.util.UUID;

/** Published language của context Payment. */
public final class PaymentEvents {

    private PaymentEvents() {
    }

    public record PaymentSucceeded(UUID paymentId, UUID bookingId, Money amount,
                                   LocalDateTime occurredAt) implements DomainEvent {
    }

    public record PaymentFailed(UUID paymentId, UUID bookingId, LocalDateTime occurredAt) implements DomainEvent {
    }
}
