package com.cowork.dataaccess.entity;

import java.util.EnumSet;
import java.util.Set;

/** Vòng đời đơn đặt chỗ — xem State Diagram 3.1 trong docs/diagrams.md. */
public enum BookingStatus {
    HELD,
    CONFIRMED,
    FAILED,
    EXPIRED,
    CHECKED_IN,
    OVERSTAY,
    CHECKED_OUT,
    COMPLETED,
    CANCELLED,
    NO_SHOW,
    REFUND_PENDING,
    REFUNDED;

    /** BR-01: các trạng thái đang chiếm vị trí trong khung giờ của đơn. */
    public static final Set<BookingStatus> SLOT_OCCUPYING = EnumSet.of(HELD, CONFIRMED, CHECKED_IN, OVERSTAY);
}
