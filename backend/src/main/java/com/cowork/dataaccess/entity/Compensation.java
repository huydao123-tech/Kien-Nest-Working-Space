package com.cowork.dataaccess.entity;

import com.cowork.dataaccess.enums.CompensationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Đền bù cho khách hàng khi gặp sự cố. Bảng: {@code compensation}. */
@Entity
@Table(name = "compensation")
public class Compensation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompensationType type;

    /** {@code value} là từ khóa của H2 nên tên cột phải đặt trong ngoặc kép. */
    @Column(name = "\"value\"", nullable = false)
    private long value;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id")
    private Voucher voucher;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private Staff createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private Staff approvedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Compensation() {
    }

    public Compensation(Booking booking, Ticket ticket, CompensationType type, long value,
                        Voucher voucher, Staff createdBy, Staff approvedBy) {
        this.booking = booking;
        this.ticket = ticket;
        this.type = type;
        this.value = value;
        this.voucher = voucher;
        this.createdBy = createdBy;
        this.approvedBy = approvedBy;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public void setTicket(Ticket ticket) {
        this.ticket = ticket;
    }

    public CompensationType getType() {
        return type;
    }

    public void setType(CompensationType type) {
        this.type = type;
    }

    public long getValue() {
        return value;
    }

    public void setValue(long value) {
        this.value = value;
    }

    public Voucher getVoucher() {
        return voucher;
    }

    public void setVoucher(Voucher voucher) {
        this.voucher = voucher;
    }

    public Staff getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Staff createdBy) {
        this.createdBy = createdBy;
    }

    public Staff getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Staff approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

