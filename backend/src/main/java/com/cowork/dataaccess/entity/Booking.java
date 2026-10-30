package com.cowork.dataaccess.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Đơn đặt chỗ: một khách đặt một vị trí trong một khung giờ.
 * Chỉ chứa dữ liệu; mọi quy tắc chuyển trạng thái nằm ở {@code BookingLifecycle} (tầng business).
 * Các khoản phải thu và lịch sử trạng thái được lưu cùng đơn nhưng ở bảng riêng.
 */
@Entity
@Table(name = "booking")
public class Booking {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 12)
    private String code;

    /** Nội dung mã QR: ngẫu nhiên, không đoán được, tách biệt với mã hiển thị. */
    @Column(nullable = false, unique = true, length = 64)
    private String qrToken;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Embedded
    private TimeRange period;

    private int partySize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    private long paidAmount;

    private LocalDateTime holdExpiresAt;
    private LocalDateTime checkedInAt;
    private LocalDateTime checkedOutAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** Kiểu wrapper để Spring Data nhận biết entity mới (version null) và gọi persist thay vì merge. */
    @Version
    private Long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "booking_charge", joinColumns = @JoinColumn(name = "booking_id"))
    @OrderColumn(name = "line_no")
    private List<BookingCharge> charges = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "booking_status_history", joinColumns = @JoinColumn(name = "booking_id"))
    @OrderColumn(name = "line_no")
    private List<StatusChange> statusHistory = new ArrayList<>();

    public Booking() {
    }

    /** Tổng các khoản phải thu. */
    public Money getTotal() {
        return charges.stream().map(BookingCharge::money).reduce(Money.ZERO, Money::plus);
    }

    public Money getPaid() {
        return Money.vnd(paidAmount);
    }

    public Money getOutstanding() {
        return getTotal().minus(getPaid());
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getQrToken() {
        return qrToken;
    }

    public void setQrToken(String qrToken) {
        this.qrToken = qrToken;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public void setResourceId(UUID resourceId) {
        this.resourceId = resourceId;
    }

    public TimeRange getPeriod() {
        return period;
    }

    public void setPeriod(TimeRange period) {
        this.period = period;
    }

    public int getPartySize() {
        return partySize;
    }

    public void setPartySize(int partySize) {
        this.partySize = partySize;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public long getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(long paidAmount) {
        this.paidAmount = paidAmount;
    }

    public LocalDateTime getHoldExpiresAt() {
        return holdExpiresAt;
    }

    public void setHoldExpiresAt(LocalDateTime holdExpiresAt) {
        this.holdExpiresAt = holdExpiresAt;
    }

    public LocalDateTime getCheckedInAt() {
        return checkedInAt;
    }

    public void setCheckedInAt(LocalDateTime checkedInAt) {
        this.checkedInAt = checkedInAt;
    }

    public LocalDateTime getCheckedOutAt() {
        return checkedOutAt;
    }

    public void setCheckedOutAt(LocalDateTime checkedOutAt) {
        this.checkedOutAt = checkedOutAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<BookingCharge> getCharges() {
        return charges;
    }

    public List<StatusChange> getStatusHistory() {
        return statusHistory;
    }
}
