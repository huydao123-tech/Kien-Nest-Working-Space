package com.cowork.business.service;

import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.BookingStatus;
import com.cowork.dataaccess.entity.Money;
import com.cowork.dataaccess.entity.Payment;
import com.cowork.dataaccess.entity.PaymentMethod;
import com.cowork.dataaccess.entity.PaymentStatus;
import com.cowork.dataaccess.integration.PaymentGateway;
import com.cowork.dataaccess.integration.PaymentGateway.CheckoutSession;
import com.cowork.dataaccess.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** Giao dịch thanh toán — xem State Diagram 3.3 trong docs/diagrams.md. */
@Service
@Transactional
public class PaymentService {

    private static final Set<PaymentStatus> OPEN = EnumSet.of(PaymentStatus.INITIATED, PaymentStatus.PENDING);
    private static final Set<PaymentStatus> AWAITING_RESULT = EnumSet.of(PaymentStatus.PENDING, PaymentStatus.UNCONFIRMED);

    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final BookingService bookingService;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, PaymentGateway gateway, BookingService bookingService, Clock clock) {
        this.payments = payments;
        this.gateway = gateway;
        this.bookingService = bookingService;
        this.clock = clock;
    }

    /** UC04 bước 1–2. Gọi lại khi đã có giao dịch đang mở thì trả về giao dịch đó. */
    public Payment initiate(UUID bookingId, PaymentMethod method) {
        Booking booking = bookingService.get(bookingId);
        if (booking.getStatus() != BookingStatus.HELD) {
            throw new ConflictException("BOOKING_NOT_PAYABLE", "Đơn đang ở trạng thái " + booking.getStatus());
        }
        for (Payment existing : payments.findByBookingId(bookingId)) {
            if (OPEN.contains(existing.getStatus())) {
                return existing;
            }
        }
        Money amount = booking.getOutstanding();
        if (!amount.isPositive()) {
            throw new DomainException("NOTHING_TO_PAY", "Đơn không có khoản cần thanh toán");
        }
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setBookingId(bookingId);
        payment.setAmount(amount.amount());
        payment.setMethod(method);
        payment.setStatus(PaymentStatus.INITIATED);
        // Gửi kèm cho cổng để không tạo giao dịch trùng nếu hệ thống gọi lại (FR-PAY-02).
        payment.setIdempotencyKey(bookingId + ":" + payment.getId());
        payment.setCreatedAt(now());

        CheckoutSession session = gateway.createCheckout(payment.getIdempotencyKey(), amount,
                "Thanh toan don " + booking.getCode(), booking.getHoldExpiresAt());
        payment.setGatewayTransactionId(session.gatewayTransactionId());
        payment.setCheckoutUrl(session.checkoutUrl());
        payment.setStatus(PaymentStatus.PENDING);
        return payments.save(payment);
    }

    /**
     * UC04 bước 4–6: xử lý kết quả đã được xác thực chữ ký từ cổng, rồi cập nhật đơn đặt chỗ.
     * Lưu giao dịch và cập nhật đơn nằm chung một transaction: một bên lỗi thì cả hai cùng hủy,
     * cổng sẽ gửi lại webhook. Webhook trùng lặp bị bỏ qua.
     */
    public Payment handleGatewayResult(String gatewayTransactionId, boolean success, Money reportedAmount) {
        Payment payment = payments.findByGatewayTransactionId(gatewayTransactionId)
                .orElseThrow(() -> new NotFoundException("Giao dịch", gatewayTransactionId));
        if (success) {
            if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
                return payment;
            }
            requireAwaitingResult(payment);
            if (reportedAmount.amount() != payment.getAmount()) {
                throw new DomainException("AMOUNT_MISMATCH",
                        "Cổng báo số tiền " + reportedAmount.amount() + " khác " + payment.getAmount());
            }
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setCompletedAt(now());
            payments.save(payment);
            bookingService.onPaymentSucceeded(payment.getBookingId(), Money.vnd(payment.getAmount()));
        } else {
            if (payment.getStatus() == PaymentStatus.FAILED) {
                return payment;
            }
            requireAwaitingResult(payment);
            payment.setStatus(PaymentStatus.FAILED);
            payment.setCompletedAt(now());
            payments.save(payment);
            bookingService.onPaymentFailed(payment.getBookingId());
        }
        return payment;
    }

    private static void requireAwaitingResult(Payment payment) {
        if (!AWAITING_RESULT.contains(payment.getStatus())) {
            throw new DomainException("INVALID_PAYMENT_STATE", "Giao dịch đang ở trạng thái " + payment.getStatus());
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
