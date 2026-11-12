package com.cowork.dataaccess.entity;

import com.cowork.dataaccess.enums.ChargeType;
import com.cowork.dataaccess.valueobject.Money;
import com.cowork.dataaccess.valueobject.TimeRange;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Một khoản phải thu của đơn, ứng với một khoảng thời gian sử dụng. Bảng: {@code booking_charge}.
 * Khóa chính ghép (booking_id, line_no); {@code lineNo} là thứ tự dòng trong đơn, bắt đầu từ 0.
 */
@Entity
@Table(name = "booking_charge")
@IdClass(BookingCharge.Key.class)
public class BookingCharge {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Id
    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_type", nullable = false, length = 30)
    private ChargeType type;

    @Column(nullable = false)
    private long amount;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public BookingCharge() {
    }

    /** Tạo dòng mới nối vào cuối danh sách khoản phải thu của đơn (chưa thêm vào danh sách). */
    public static BookingCharge of(Booking booking, ChargeType type, Money amount, TimeRange period, LocalDateTime now) {
        BookingCharge charge = new BookingCharge();
        charge.booking = booking;
        charge.lineNo = booking.getCharges().size();
        charge.type = type;
        charge.amount = amount.amount();
        charge.startAt = period.startAt();
        charge.endAt = period.endAt();
        charge.createdAt = now;
        return charge;
    }

    public Money money() {
        return Money.vnd(amount);
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public int getLineNo() {
        return lineNo;
    }

    public void setLineNo(int lineNo) {
        this.lineNo = lineNo;
    }

    public ChargeType getType() {
        return type;
    }

    public void setType(ChargeType type) {
        this.type = type;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public void setEndAt(LocalDateTime endAt) {
        this.endAt = endAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /** Khóa chính ghép: tên field trùng với hai field {@code @Id} ở trên, {@code booking} giữ id của đơn. */
    public static class Key implements Serializable {

        private Long booking;
        private int lineNo;

        public Key() {
        }

        public Key(Long booking, int lineNo) {
            this.booking = booking;
            this.lineNo = lineNo;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other && lineNo == other.lineNo && Objects.equals(booking, other.booking);
        }

        @Override
        public int hashCode() {
            return Objects.hash(booking, lineNo);
        }
    }
}
