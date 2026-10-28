package com.cowork.payment.application;

import com.cowork.shared.domain.Money;

import java.time.LocalDateTime;
import java.util.UUID;

/** Anti-corruption layer: Payment chỉ biết những gì nó cần về một đơn đặt chỗ. */
public interface PayableBookingProvider {

    PayableBooking payableBooking(UUID bookingId);

    record PayableBooking(UUID bookingId, String code, Money amount, LocalDateTime payBefore) {
    }
}
