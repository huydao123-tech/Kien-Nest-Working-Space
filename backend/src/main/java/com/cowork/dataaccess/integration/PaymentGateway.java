package com.cowork.dataaccess.integration;

import com.cowork.dataaccess.entity.Money;

import java.time.LocalDateTime;

/** Cổng ra tới nhà cung cấp thanh toán (VietQR, ví điện tử, thẻ). */
public interface PaymentGateway {

    CheckoutSession createCheckout(String idempotencyKey, Money amount, String description, LocalDateTime expiresAt);

    record CheckoutSession(String gatewayTransactionId, String checkoutUrl) {
    }
}
