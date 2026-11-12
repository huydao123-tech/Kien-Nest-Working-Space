package com.cowork.dataaccess.entity;

import com.cowork.dataaccess.enums.BookingStatus;
import com.cowork.dataaccess.enums.ChargeType;
import com.cowork.dataaccess.valueobject.Money;
import com.cowork.dataaccess.valueobject.TimeRange;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Đơn đặt chỗ: một khách đặt một vị trí trong một khung giờ.
 * Chỉ chứa dữ liệu; mọi quy tắc chuyển trạng thái nằm ở {@code BookingService}.
 * Các khoản phải thu và lịch sử trạng thái được lưu cùng đơn nhưng ở bảng riêng.
 */
@Entity
@Table(name = "booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 12)
    private String code;

    /** Nội dung mã QR: ngẫu nhiên, không đoán được, tách biệt với mã hiển thị. */
    @Column(nullable = false, unique = true, length = 64)
    private String qrToken;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private SpaceResource resource;

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

    // Đổi chỗ đang chờ khách trả phần chênh (booking.md Prompt 2). Cả năm cột cùng rỗng khi không có.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pending_resource_id")
    private SpaceResource pendingResource;

    @Column(name = "pending_start_at")
    private LocalDateTime pendingStartAt;

    @Column(name = "pending_end_at")
    private LocalDateTime pendingEndAt;

    /** Số tiền khách phải trả thêm để đổi sang chỗ mới. */
    @Column(name = "pending_amount")
    private Long pendingAmount;

    @Column(name = "pending_expires_at")
    private LocalDateTime pendingExpiresAt;

    // Hai danh sách cùng EAGER nên nạp bằng câu SELECT riêng, tránh JOIN hai bag một lúc (MultipleBagFetchException).
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SELECT)
    @OrderBy("lineNo")
    private List<BookingCharge> charges = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SELECT)
    @OrderBy("lineNo")
    private List<BookingStatusHistory> statusHistory = new ArrayList<>();

    /** Add-on thuê kèm (FR-BOOK-02). */
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SELECT)
    @OrderBy("id")
    private List<BookingAddon> addons = new ArrayList<>();

    public Booking() {
    }

    /** FR-BOOK-06: tổng tiền = các khoản phải thu − các khoản giảm trừ (voucher, membership). */
    public Money getTotal() {
        Money charged = Money.ZERO;
        Money deducted = Money.ZERO;
        for (BookingCharge charge : charges) {
            if (charge.getType().isDeduction()) {
                deducted = deducted.plus(charge.money());
            } else {
                charged = charged.plus(charge.money());
            }
        }
        return charged.minus(deducted);
    }

    /** Tổng các khoản thuộc một loại, ví dụ tiền thuê {@code RENTAL}. */
    public Money sumOf(ChargeType type) {
        return charges.stream().filter(c -> c.getType() == type).map(BookingCharge::money).reduce(Money.ZERO, Money::plus);
    }

    public List<BookingAddon> getAddons() {
        return addons;
    }

    public Money getPaid() {
        return Money.vnd(paidAmount);
    }

    public Money getOutstanding() {
        return getTotal().minus(getPaid());
    }

    /** Có yêu cầu đổi chỗ đang chờ thanh toán phần chênh hay không. */
    public boolean hasPendingChange() {
        return pendingExpiresAt != null;
    }

    /** Khung giờ của chỗ mới đang chờ thanh toán; rỗng nếu không có. */
    public TimeRange getPendingPeriod() {
        return hasPendingChange() ? new TimeRange(pendingStartAt, pendingEndAt) : null;
    }

    public SpaceResource getPendingResource() {
        return pendingResource;
    }

    public void setPendingResource(SpaceResource pendingResource) {
        this.pendingResource = pendingResource;
    }

    public LocalDateTime getPendingStartAt() {
        return pendingStartAt;
    }

    public void setPendingStartAt(LocalDateTime pendingStartAt) {
        this.pendingStartAt = pendingStartAt;
    }

    public LocalDateTime getPendingEndAt() {
        return pendingEndAt;
    }

    public void setPendingEndAt(LocalDateTime pendingEndAt) {
        this.pendingEndAt = pendingEndAt;
    }

    public Long getPendingAmount() {
        return pendingAmount;
    }

    public void setPendingAmount(Long pendingAmount) {
        this.pendingAmount = pendingAmount;
    }

    public LocalDateTime getPendingExpiresAt() {
        return pendingExpiresAt;
    }

    public void setPendingExpiresAt(LocalDateTime pendingExpiresAt) {
        this.pendingExpiresAt = pendingExpiresAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public SpaceResource getResource() {
        return resource;
    }

    public void setResource(SpaceResource resource) {
        this.resource = resource;
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

    public List<BookingStatusHistory> getStatusHistory() {
        return statusHistory;
    }
}
