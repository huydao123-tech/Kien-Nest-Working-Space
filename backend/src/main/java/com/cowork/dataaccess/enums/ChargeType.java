package com.cowork.dataaccess.enums;

/** Loại khoản của đơn. Tổng tiền = khoản phải thu − khoản giảm trừ (FR-BOOK-06). */
public enum ChargeType {
    /** Tiền thuê khung giờ đã đặt, tính theo block (BR-03, BR-04). */
    RENTAL(false),
    /** Tiền thuê add-on: đơn giá block × số block × số lượng (FR-BOOK-02). */
    ADDON(false),
    /** Tiền gia hạn tại chỗ (UC08). */
    EXTENSION(false),
    /** Phụ phí ngồi quá giờ (BR-06). */
    OVERSTAY_SURCHARGE(false),
    /** Giảm giá voucher (BR-12) — giảm trừ. */
    VOUCHER_DISCOUNT(true),
    /** Giờ membership đã khấu trừ, quy ra tiền (BR-13) — giảm trừ. */
    MEMBERSHIP_DEDUCTION(true);

    private final boolean deduction;

    ChargeType(boolean deduction) {
        this.deduction = deduction;
    }

    /** {@code true} nếu khoản này được trừ khỏi tổng tiền thay vì cộng vào. */
    public boolean isDeduction() {
        return deduction;
    }
}
