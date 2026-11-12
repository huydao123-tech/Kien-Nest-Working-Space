package com.cowork.business.service;

import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.enums.BookingStatus;
import com.cowork.dataaccess.valueobject.Money;
import com.cowork.dataaccess.entity.Payment;
import com.cowork.dataaccess.enums.PaymentMethod;
import com.cowork.dataaccess.enums.PaymentStatus;
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

    /**
     * UC04 bước 1–2: thanh toán đơn đang giữ chỗ (HELD), hoặc phần chênh của yêu cầu đổi chỗ đang chờ (đơn CONFIRMED,
     * booking.md Prompt 2). Gọi lại khi đã có giao dịch đang mở đúng số tiền thì trả về giao dịch đó; giao dịch đang mở
     * khác số tiền (của yêu cầu đổi chỗ trước) chuyển UNCONFIRMED — tiền về sau đó sẽ được hoàn.
     */
    public Payment initiate(Long bookingId, PaymentMethod method) {
        Booking booking = bookingService.get(bookingId);
        Money amount;
        LocalDateTime expiresAt;
        if (booking.getStatus() == BookingStatus.HELD) {
            amount = booking.getOutstanding();
            expiresAt = booking.getHoldExpiresAt();
        } else if (booking.getStatus() == BookingStatus.CONFIRMED && booking.hasPendingChange()) {
            amount = Money.vnd(booking.getPendingAmount());
            expiresAt = booking.getPendingExpiresAt();
        } else {
            throw new ConflictException("BOOKING_NOT_PAYABLE", "Đơn đang ở trạng thái " + booking.getStatus());
        }
        for (Payment existing : payments.findByBookingId(bookingId)) {
            if (!OPEN.contains(existing.getStatus())) {
                continue;
            }
            if (existing.getAmount() == amount.amount()) {
                return existing;
            }
            existing.setStatus(PaymentStatus.UNCONFIRMED);
            // Ghi ngay để chỉ mục "mỗi đơn một giao dịch đang mở" không chặn giao dịch mới.
            payments.saveAndFlush(existing);
        }
        if (!amount.isPositive()) {
            throw new DomainException("NOTHING_TO_PAY", "Đơn không có khoản cần thanh toán");
        }
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(amount.amount());
        payment.setMethod(method);
        payment.setStatus(PaymentStatus.INITIATED);
        // Gửi kèm cho cổng để không tạo giao dịch trùng nếu hệ thống gọi lại (FR-PAY-02).
        // Id tự tăng chỉ có sau khi lưu, nên phần sau dấu ":" là một chuỗi ngẫu nhiên.
        payment.setIdempotencyKey(bookingId + ":" + UUID.randomUUID());
        payment.setCreatedAt(now());

        CheckoutSession session = gateway.createCheckout(payment.getIdempotencyKey(), amount,
                "Thanh toan don " + booking.getCode(), expiresAt);
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
            bookingService.onPaymentSucceeded(payment.getBooking().getId(), Money.vnd(payment.getAmount()));
        } else {
            if (payment.getStatus() == PaymentStatus.FAILED) {
                return payment;
            }
            requireAwaitingResult(payment);
            payment.setStatus(PaymentStatus.FAILED);
            payment.setCompletedAt(now());
            payments.save(payment);
            bookingService.onPaymentFailed(payment.getBooking().getId());
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
