package com.cowork.payment.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findByGatewayTransactionId(String gatewayTransactionId);

    List<Payment> findByBookingId(UUID bookingId);
}
