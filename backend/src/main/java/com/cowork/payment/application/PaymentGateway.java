package com.cowork.payment.application;

import com.cowork.shared.domain.Money;

import java.time.LocalDateTime;

/** Cổng ra tới nhà cung cấp thanh toán (VietQR, ví điện tử, thẻ). */
public interface PaymentGateway {

    CheckoutSession createCheckout(String idempotencyKey, Money amount, String description, LocalDateTime expiresAt);

    record CheckoutSession(String gatewayTransactionId, String checkoutUrl) {
    }
}
