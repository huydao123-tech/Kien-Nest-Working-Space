package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.Payment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findByGatewayTransactionId(String gatewayTransactionId);

    List<Payment> findByBookingId(UUID bookingId);
}
