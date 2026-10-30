package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.Payment;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaPaymentRepository implements PaymentRepository {

    private final SpringDataPaymentRepository jpa;

    JpaPaymentRepository(SpringDataPaymentRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Payment save(Payment payment) {
        return jpa.save(payment);
    }

    @Override
    public Optional<Payment> findByGatewayTransactionId(String gatewayTransactionId) {
        return jpa.findByGatewayTransactionId(gatewayTransactionId);
    }

    @Override
    public List<Payment> findByBookingId(UUID bookingId) {
        return jpa.findByBookingId(bookingId);
    }
}
