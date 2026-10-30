package com.cowork.presentation.controller;

import com.cowork.dataaccess.integration.WebhookSignature;
import com.cowork.presentation.controller.PaymentController.PaymentResponse;
import com.cowork.presentation.controller.PaymentWebhookHandler.WebhookPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Chỉ bật ở dev/test: giả lập cổng thanh toán gửi webhook đã ký về hệ thống. */
@RestController
@ConditionalOnProperty(name = "payment.sandbox.enabled", havingValue = "true")
class SandboxPaymentController {

    private final WebhookSignature signature;
    private final ObjectMapper objectMapper;
    private final PaymentWebhookHandler webhookHandler;

    SandboxPaymentController(WebhookSignature signature, ObjectMapper objectMapper, PaymentWebhookHandler webhookHandler) {
        this.signature = signature;
        this.objectMapper = objectMapper;
        this.webhookHandler = webhookHandler;
    }

    @PostMapping("/api/payments/sandbox/{gatewayTransactionId}/complete")
    PaymentResponse complete(@PathVariable String gatewayTransactionId,
                             @RequestParam(defaultValue = "SUCCESS") String result,
                             @RequestParam long amount) throws JsonProcessingException {
        String body = objectMapper.writeValueAsString(new WebhookPayload(gatewayTransactionId, result, amount));
        return PaymentResponse.from(webhookHandler.handle(body, signature.sign(body)));
    }
}
