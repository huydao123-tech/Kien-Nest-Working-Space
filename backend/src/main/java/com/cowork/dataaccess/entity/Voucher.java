package com.cowork.dataaccess.entity;

import com.cowork.dataaccess.enums.DiscountType;
import com.cowork.dataaccess.enums.VoucherStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Mã giảm giá và điều kiện khuyến mãi. Bảng: {@code voucher}. */
@Entity
@Table(name = "voucher")
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false)
    private long discountValue;

    @Column(name = "min_order_amount", nullable = false)
    private long minOrderAmount = 0L;

    @Column(name = "max_discount_amount")
    private Long maxDiscountAmount;

    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDateTime validTo;

    @Column(name = "total_limit")
    private Integer totalLimit;

    @Column(name = "per_customer_limit", nullable = false)
    private int perCustomerLimit = 1;

    @Column(name = "used_count", nullable = false)
    private int usedCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VoucherStatus status = VoucherStatus.ACTIVE;

    @Version
    private Long version;

    /** Loại không gian được áp voucher; rỗng = áp cho mọi loại. */
    @OneToMany(mappedBy = "voucher", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VoucherResourceType> resourceTypes = new ArrayList<>();

    public Voucher() {
    }

    public Voucher(String code, String name, DiscountType discountType, long discountValue,
                   LocalDateTime validFrom, LocalDateTime validTo) {
        this.code = code;
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.status = VoucherStatus.ACTIVE;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public void setDiscountType(DiscountType discountType) {
        this.discountType = discountType;
    }

    public long getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(long discountValue) {
        this.discountValue = discountValue;
    }

    public long getMinOrderAmount() {
        return minOrderAmount;
    }

    public void setMinOrderAmount(long minOrderAmount) {
        this.minOrderAmount = minOrderAmount;
    }

    public Long getMaxDiscountAmount() {
        return maxDiscountAmount;
    }

    public void setMaxDiscountAmount(Long maxDiscountAmount) {
        this.maxDiscountAmount = maxDiscountAmount;
    }

    public LocalDateTime getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDateTime validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDateTime getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDateTime validTo) {
        this.validTo = validTo;
    }

    public Integer getTotalLimit() {
        return totalLimit;
    }

    public void setTotalLimit(Integer totalLimit) {
        this.totalLimit = totalLimit;
    }

    public int getPerCustomerLimit() {
        return perCustomerLimit;
    }

    public void setPerCustomerLimit(int perCustomerLimit) {
        this.perCustomerLimit = perCustomerLimit;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public void setUsedCount(int usedCount) {
        this.usedCount = usedCount;
    }

    public VoucherStatus getStatus() {
        return status;
    }

    public void setStatus(VoucherStatus status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public List<VoucherResourceType> getResourceTypes() {
        return resourceTypes;
    }

    public void setResourceTypes(List<VoucherResourceType> resourceTypes) {
        this.resourceTypes = resourceTypes;
    }
}
