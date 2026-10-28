package com.cowork.booking.domain;

import com.cowork.shared.domain.Money;
import com.cowork.shared.domain.TimeRange;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.LocalDateTime;

/** Một khoản phải thu của đơn, ứng với một khoảng thời gian sử dụng. Lưu ở bảng {@code booking_charge}. */
@Embeddable
public record BookingCharge(
        @Enumerated(EnumType.STRING) @Column(name = "charge_type", nullable = false, length = 30) ChargeType type,
        @Column(name = "amount", nullable = false) long amount,
        @Column(name = "start_at", nullable = false) LocalDateTime startAt,
        @Column(name = "end_at", nullable = false) LocalDateTime endAt,
        @Column(name = "created_at", nullable = false) LocalDateTime createdAt) {

    static BookingCharge of(ChargeType type, Money amount, TimeRange period, LocalDateTime now) {
        return new BookingCharge(type, amount.amount(), period.startAt(), period.endAt(), now);
    }

    public Money money() {
        return Money.vnd(amount);
    }
}
