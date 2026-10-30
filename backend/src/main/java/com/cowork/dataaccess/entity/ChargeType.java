package com.cowork.dataaccess.entity;

public enum ChargeType {
    /** Tiền thuê khung giờ đã đặt. */
    RENTAL,
    /** Tiền gia hạn tại chỗ (UC08). */
    EXTENSION,
    /** Phụ phí ngồi quá giờ (BR-06). */
    OVERSTAY_SURCHARGE
}
