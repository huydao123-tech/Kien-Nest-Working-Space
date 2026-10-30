package com.cowork.dataaccess.entity;

import jakarta.persistence.Embeddable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Khoảng thời gian nửa mở [startAt, endAt). Hai khoảng chạm mép nhau không bị coi là chồng lấn.
 * Kiểm tra dữ liệu người dùng nhập (giờ kết thúc phải sau giờ bắt đầu) nằm ở tầng business,
 * xem {@code BookingLifecycle.period(...)}.
 */
@Embeddable
public record TimeRange(LocalDateTime startAt, LocalDateTime endAt) {

    public TimeRange {
        Objects.requireNonNull(startAt, "startAt");
        Objects.requireNonNull(endAt, "endAt");
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("endAt phải sau startAt");
        }
    }

    public boolean overlaps(TimeRange other) {
        return startAt.isBefore(other.endAt) && other.startAt.isBefore(endAt);
    }

    public Duration duration() {
        return Duration.between(startAt, endAt);
    }

    public TimeRange withEnd(LocalDateTime newEnd) {
        return new TimeRange(startAt, newEnd);
    }
}
