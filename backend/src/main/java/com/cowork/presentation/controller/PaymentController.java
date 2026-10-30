package com.cowork.presentation.controller;

import com.cowork.business.service.PaymentService;
import com.cowork.dataaccess.entity.Payment;
import com.cowork.dataaccess.entity.PaymentMethod;
import com.cowork.dataaccess.entity.PaymentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
class PaymentController {

    static final String SIGNATURE_HEADER = "X-Signature";

    private final PaymentService paymentService;
    private final PaymentWebhookHandler webhookHandler;

    PaymentController(PaymentService paymentService, PaymentWebhookHandler webhookHandler) {
        this.paymentService = paymentService;
        this.webhookHandler = webhookHandler;
    }

    @PostMapping("/api/bookings/{bookingId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    PaymentResponse initiate(@PathVariable UUID bookingId, @Valid @RequestBody InitiatePaymentRequest request) {
        return PaymentResponse.from(paymentService.initiate(bookingId, request.method()));
    }

    /** Endpoint cổng thanh toán gọi về. Body nhận nguyên văn để kiểm tra chữ ký trước khi parse. */
    @PostMapping("/api/payments/webhook")
    PaymentResponse webhook(@RequestBody String rawBody,
                            @RequestHeader(name = SIGNATURE_HEADER, required = false) String signature) {
        return PaymentResponse.from(webhookHandler.handle(rawBody, signature));
    }

    record InitiatePaymentRequest(@NotNull PaymentMethod method) {
    }

    record PaymentResponse(UUID paymentId, UUID bookingId, long amount, PaymentStatus status,
                           String gatewayTransactionId, String checkoutUrl) {

        static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getBookingId(), p.getAmount(), p.getStatus(),
                    p.getGatewayTransactionId(), p.getCheckoutUrl());
        }
    }
}
