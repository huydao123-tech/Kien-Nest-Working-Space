package com.cowork.booking.domain;

import com.cowork.shared.domain.Money;

import java.time.Duration;
import java.time.LocalDateTime;

/** BR-15 (giả định, chờ khách hàng xác nhận): ≥ 24h hoàn 100%, 2h–24h hoàn 50%, &lt; 2h không hoàn. */
public class CancellationPolicy {

    public Money refundFor(Money paid, LocalDateTime startAt, LocalDateTime now) {
        Duration noticeGiven = Duration.between(now, startAt);
        if (noticeGiven.compareTo(Duration.ofHours(24)) >= 0) {
            return paid;
        }
        if (noticeGiven.compareTo(Duration.ofHours(2)) >= 0) {
            return paid.percent(50);
        }
        return Money.ZERO;
    }
}
