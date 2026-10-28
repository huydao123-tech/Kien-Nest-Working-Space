package com.cowork.payment.domain;

import com.cowork.payment.domain.event.PaymentEvents.PaymentFailed;
import com.cowork.payment.domain.event.PaymentEvents.PaymentSucceeded;
import com.cowork.shared.domain.AggregateRoot;
import com.cowork.shared.domain.DomainException;
import com.cowork.shared.domain.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.UUID;

/** Một giao dịch thanh toán qua cổng cho một đơn đặt chỗ. */
@Entity
@Table(name = "payment")
public class Payment extends AggregateRoot {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID bookingId;

    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    /** Gửi kèm khi tạo giao dịch để cổng không tạo trùng nếu hệ thống gọi lại (FR-PAY-02). */
    @Column(nullable = false, unique = true, length = 80)
    private String idempotencyKey;

    @Column(unique = true, length = 80)
    private String gatewayTransactionId;

    private String checkoutUrl;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    @Version
    private Long version;

    protected Payment() {
    }

    public static Payment initiate(UUID bookingId, Money amount, PaymentMethod method, LocalDateTime now) {
        if (!amount.isPositive()) {
            throw new DomainException("NOTHING_TO_PAY", "Đơn không có khoản cần thanh toán");
        }
        Payment payment = new Payment();
        payment.id = UUID.randomUUID();
        payment.bookingId = bookingId;
        payment.amount = amount.amount();
        payment.method = method;
        payment.status = PaymentStatus.INITIATED;
        payment.idempotencyKey = bookingId + ":" + payment.id;
        payment.createdAt = now;
        return payment;
    }

    public void attachCheckout(String gatewayTransactionId, String checkoutUrl) {
        if (status != PaymentStatus.INITIATED) {
            throw new DomainException("INVALID_PAYMENT_STATE", "Giao dịch đã được gửi tới cổng thanh toán");
        }
        this.gatewayTransactionId = gatewayTransactionId;
        this.checkoutUrl = checkoutUrl;
        this.status = PaymentStatus.PENDING;
    }

    /**
     * Ghi nhận kết quả thành công từ cổng.
     *
     * @return false nếu đã ghi nhận trước đó (webhook trùng lặp) — không phát event lần nữa
     */
    public boolean succeed(Money reportedAmount, LocalDateTime now) {
        if (status == PaymentStatus.SUCCEEDED) {
            return false;
        }
        requireAwaitingResult();
        if (reportedAmount.amount() != amount) {
            throw new DomainException("AMOUNT_MISMATCH", "Cổng báo số tiền " + reportedAmount.amount() + " khác " + amount);
        }
        status = PaymentStatus.SUCCEEDED;
        completedAt = now;
        registerEvent(new PaymentSucceeded(id, bookingId, Money.vnd(amount), now));
        return true;
    }

    public boolean fail(LocalDateTime now) {
        if (status == PaymentStatus.FAILED) {
            return false;
        }
        requireAwaitingResult();
        status = PaymentStatus.FAILED;
        completedAt = now;
        registerEvent(new PaymentFailed(id, bookingId, now));
        return true;
    }

    public boolean isOpen() {
        return status == PaymentStatus.INITIATED || status == PaymentStatus.PENDING;
    }

    private void requireAwaitingResult() {
        if (!EnumSet.of(PaymentStatus.PENDING, PaymentStatus.UNCONFIRMED).contains(status)) {
            throw new DomainException("INVALID_PAYMENT_STATE", "Giao dịch đang ở trạng thái " + status);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID bookingId() {
        return bookingId;
    }

    public Money amount() {
        return Money.vnd(amount);
    }

    public PaymentMethod method() {
        return method;
    }

    public PaymentStatus status() {
        return status;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public String gatewayTransactionId() {
        return gatewayTransactionId;
    }

    public String checkoutUrl() {
        return checkoutUrl;
    }
}
