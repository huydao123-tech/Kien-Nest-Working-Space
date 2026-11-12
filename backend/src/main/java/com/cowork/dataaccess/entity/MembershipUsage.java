package com.cowork.dataaccess.entity;

import com.cowork.dataaccess.enums.MembershipUsageStatus;
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

/** Lịch sử trừ giờ của gói thành viên cho từng đơn đặt chỗ. Bảng: {@code membership_usage}. */
@Entity
@Table(name = "membership_usage")
public class MembershipUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pass_id", nullable = false)
    private MembershipPass pass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(nullable = false)
    private int minutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipUsageStatus status = MembershipUsageStatus.HELD;

    @Column(name = "used_at", nullable = false)
    private LocalDateTime usedAt;

    public MembershipUsage() {
    }

    public MembershipUsage(MembershipPass pass, Booking booking, int minutes) {
        this.pass = pass;
        this.booking = booking;
        this.minutes = minutes;
        this.status = MembershipUsageStatus.HELD;
        this.usedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public MembershipPass getPass() {
        return pass;
    }

    public void setPass(MembershipPass pass) {
        this.pass = pass;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public int getMinutes() {
        return minutes;
    }

    public void setMinutes(int minutes) {
        this.minutes = minutes;
    }

    public MembershipUsageStatus getStatus() {
        return status;
    }

    public void setStatus(MembershipUsageStatus status) {
        this.status = status;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }
}

