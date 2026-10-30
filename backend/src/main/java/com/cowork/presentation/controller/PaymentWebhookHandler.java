package com.cowork.presentation.controller;

import com.cowork.business.service.PaymentService;
import com.cowork.dataaccess.entity.Money;
import com.cowork.dataaccess.entity.Payment;
import com.cowork.dataaccess.integration.WebhookSignature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Xác thực chữ ký rồi mới đọc nội dung webhook. Dùng chung cho endpoint thật và sandbox. */
@Component
class PaymentWebhookHandler {

    private final WebhookSignature signature;
    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;

    PaymentWebhookHandler(WebhookSignature signature, ObjectMapper objectMapper, PaymentService paymentService) {
        this.signature = signature;
        this.objectMapper = objectMapper;
        this.paymentService = paymentService;
    }

    Payment handle(String rawBody, String signatureHeader) {
        if (!signature.verify(rawBody, signatureHeader)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chữ ký webhook không hợp lệ");
        }
        WebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, WebhookPayload.class);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook sai định dạng");
        }
        return paymentService.handleGatewayResult(payload.gatewayTransactionId(),
                "SUCCESS".equals(payload.status()), Money.vnd(payload.amount()));
    }

    record WebhookPayload(String gatewayTransactionId, String status, long amount) {
    }
}
