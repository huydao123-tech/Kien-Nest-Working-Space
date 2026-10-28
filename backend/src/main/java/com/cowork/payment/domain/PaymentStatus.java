package com.cowork.payment.domain;

/** Xem State Diagram 3.3 trong docs/diagrams.md. Bản này chưa cài đặt luồng hoàn tiền. */
public enum PaymentStatus {
    INITIATED,
    PENDING,
    SUCCEEDED,
    FAILED,
    UNCONFIRMED
}
