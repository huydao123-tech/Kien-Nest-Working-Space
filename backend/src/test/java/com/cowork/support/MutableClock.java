package com.cowork.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Đồng hồ điều khiển được trong test để đi qua các mốc giữ chỗ, check-in, quá giờ. */
public class MutableClock extends Clock {

    private final ZoneId zone;
    private volatile Instant instant;

    public MutableClock(ZoneId zone) {
        this.zone = zone;
        this.instant = Instant.now();
    }

    public void set(LocalDateTime localDateTime) {
        this.instant = localDateTime.atZone(zone).toInstant();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
