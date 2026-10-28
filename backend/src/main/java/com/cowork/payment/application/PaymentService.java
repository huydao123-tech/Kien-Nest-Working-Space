package com.cowork.payment.application;

import com.cowork.payment.application.PayableBookingProvider.PayableBooking;
import com.cowork.payment.application.PaymentGateway.CheckoutSession;
import com.cowork.payment.domain.Payment;
import com.cowork.payment.domain.PaymentMethod;
import com.cowork.payment.domain.PaymentRepository;
import com.cowork.shared.application.DomainEventPublisher;
import com.cowork.shared.domain.Money;
import com.cowork.shared.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class PaymentService {

    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final PayableBookingProvider bookings;
    private final DomainEventPublisher events;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, PaymentGateway gateway, PayableBookingProvider bookings,
                          DomainEventPublisher events, Clock clock) {
        this.payments = payments;
        this.gateway = gateway;
        this.bookings = bookings;
        this.events = events;
        this.clock = clock;
    }

    /** UC04 bước 1–2. Gọi lại khi đã có giao dịch đang mở thì trả về giao dịch đó. */
    public Payment initiate(UUID bookingId, PaymentMethod method) {
        PayableBooking booking = bookings.payableBooking(bookingId);
        for (Payment existing : payments.findByBookingId(bookingId)) {
            if (existing.isOpen()) {
                return existing;
            }
        }
        Payment payment = Payment.initiate(bookingId, booking.amount(), method, now());
        CheckoutSession session = gateway.createCheckout(payment.idempotencyKey(), booking.amount(),
                "Thanh toan don " + booking.code(), booking.payBefore());
        payment.attachCheckout(session.gatewayTransactionId(), session.checkoutUrl());
        return payments.save(payment);
    }

    /** UC04 bước 4–6: xử lý thông báo đã được xác thực chữ ký từ cổng. Idempotent. */
    public Payment handleGatewayResult(String gatewayTransactionId, boolean success, Money reportedAmount) {
        Payment payment = payments.findByGatewayTransactionId(gatewayTransactionId)
                .orElseThrow(() -> new NotFoundException("Giao dịch", gatewayTransactionId));
        boolean changed = success ? payment.succeed(reportedAmount, now()) : payment.fail(now());
        if (changed) {
            payments.save(payment);
            events.publishEventsOf(payment);
        }
        return payment;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
