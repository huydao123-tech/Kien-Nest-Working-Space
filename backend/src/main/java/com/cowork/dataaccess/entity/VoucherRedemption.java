package com.cowork.dataaccess.entity;

import com.cowork.dataaccess.enums.VoucherRedemptionStatus;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Áp dụng voucher giảm giá cho đơn đặt chỗ. Bảng: {@code voucher_redemption}. */
@Entity
@Table(name = "voucher_redemption")
public class VoucherRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "discount_amount", nullable = false)
    private long discountAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VoucherRedemptionStatus status = VoucherRedemptionStatus.HELD;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public VoucherRedemption() {
    }

    public VoucherRedemption(Voucher voucher, Booking booking, Customer customer, long discountAmount) {
        this.voucher = voucher;
        this.booking = booking;
        this.customer = customer;
        this.discountAmount = discountAmount;
        this.status = VoucherRedemptionStatus.HELD;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Voucher getVoucher() {
        return voucher;
    }

    public void setVoucher(Voucher voucher) {
        this.voucher = voucher;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public long getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(long discountAmount) {
        this.discountAmount = discountAmount;
    }

    public VoucherRedemptionStatus getStatus() {
        return status;
    }

    public void setStatus(VoucherRedemptionStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

