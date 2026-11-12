package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByGatewayTransactionId(String gatewayTransactionId);

    List<Payment> findByBookingId(Long bookingId);
}
