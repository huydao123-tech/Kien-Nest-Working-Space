package com.cowork.dataaccess.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Số tiền VND (không có phần lẻ). */
public record Money(long amount) {

    public static final Money ZERO = new Money(0);

    public Money {
        if (amount < 0) {
            throw new IllegalArgumentException("Số tiền không được âm: " + amount);
        }
    }

    public static Money vnd(long amount) {
        return new Money(amount);
    }

    public Money plus(Money other) {
        return new Money(amount + other.amount);
    }

    public Money minus(Money other) {
        return new Money(amount - other.amount);
    }

    public Money multiply(BigDecimal factor) {
        return new Money(BigDecimal.valueOf(amount).multiply(factor).setScale(0, RoundingMode.HALF_UP).longValueExact());
    }

    public Money percent(int percent) {
        return multiply(BigDecimal.valueOf(percent, 2));
    }

    public boolean isZero() {
        return amount == 0;
    }

    public boolean isPositive() {
        return amount > 0;
    }
}
