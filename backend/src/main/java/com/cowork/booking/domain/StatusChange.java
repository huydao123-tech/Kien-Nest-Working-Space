package com.cowork.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.LocalDateTime;

/** Một lần đơn đổi trạng thái. Lưu ở bảng {@code booking_status_history}; {@code fromStatus} rỗng ở lần tạo đơn. */
@Embeddable
public record StatusChange(
        @Enumerated(EnumType.STRING) @Column(name = "from_status", length = 20) BookingStatus fromStatus,
        @Enumerated(EnumType.STRING) @Column(name = "to_status", nullable = false, length = 20) BookingStatus toStatus,
        @Column(name = "changed_at", nullable = false) LocalDateTime changedAt) {
}
