package com.cowork.dataaccess.entity;

import com.cowork.dataaccess.enums.BookingStatus;
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
 * Một lần đơn đổi trạng thái. Bảng: {@code booking_status_history}; {@code fromStatus} rỗng ở lần tạo đơn.
 * Khóa chính ghép (booking_id, line_no); {@code lineNo} là thứ tự dòng trong đơn, bắt đầu từ 0.
 */
@Entity
@Table(name = "booking_status_history")
@IdClass(BookingStatusHistory.Key.class)
public class BookingStatusHistory {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Id
    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private BookingStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private BookingStatus toStatus;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    public BookingStatusHistory() {
    }

    /** Tạo dòng mới nối vào cuối lịch sử trạng thái của đơn (chưa thêm vào danh sách). */
    public BookingStatusHistory(Booking booking, BookingStatus fromStatus, BookingStatus toStatus, LocalDateTime changedAt) {
        this.booking = booking;
        this.lineNo = booking.getStatusHistory().size();
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedAt = changedAt;
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

    public BookingStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(BookingStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public BookingStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(BookingStatus toStatus) {
        this.toStatus = toStatus;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
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
