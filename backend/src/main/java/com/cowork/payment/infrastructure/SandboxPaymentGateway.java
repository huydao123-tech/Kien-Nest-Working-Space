package com.cowork.payment.infrastructure;

import com.cowork.payment.application.PaymentGateway;
import com.cowork.shared.domain.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Cổng giả lập cho môi trường dev/test. Kết quả thanh toán được bắn về qua
 * {@code POST /api/payments/sandbox/{txn}/complete}. Thay bằng adapter của nhà cung cấp thật khi tích hợp.
 */
@Component
class SandboxPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(SandboxPaymentGateway.class);

    @Override
    public CheckoutSession createCheckout(String idempotencyKey, Money amount, String description, LocalDateTime expiresAt) {
        String txn = "SBX-" + UUID.nameUUIDFromBytes(idempotencyKey.getBytes());
        log.info("[sandbox] Tạo giao dịch {} số tiền {} VND, hết hạn {}", txn, amount.amount(), expiresAt);
        return new CheckoutSession(txn, "/api/payments/sandbox/" + txn + "/complete");
    }
}
