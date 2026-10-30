package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataPaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByGatewayTransactionId(String gatewayTransactionId);

    List<Payment> findByBookingId(UUID bookingId);
}
