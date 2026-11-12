package com.cowork.dataaccess.entity;

import com.cowork.dataaccess.enums.AddonStatus;
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

/** Dịch vụ / thiết bị bổ sung thuê kèm (màn hình, máy chiếu...). Bảng: {@code addon}. */
@Entity
@Table(name = "addon")
public class Addon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int stock;

    @Column(name = "price_per_block", nullable = false)
    private long pricePerBlock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AddonStatus status = AddonStatus.ACTIVE;

    public Addon() {
    }

    public Addon(Branch branch, String code, String name, int stock, long pricePerBlock) {
        this.branch = branch;
        this.code = code;
        this.name = name;
        this.stock = stock;
        this.pricePerBlock = pricePerBlock;
        this.status = AddonStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Branch getBranch() {
        return branch;
    }

    public void setBranch(Branch branch) {
        this.branch = branch;
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

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public long getPricePerBlock() {
        return pricePerBlock;
    }

    public void setPricePerBlock(long pricePerBlock) {
        this.pricePerBlock = pricePerBlock;
    }

    public AddonStatus getStatus() {
        return status;
    }

    public void setStatus(AddonStatus status) {
        this.status = status;
    }
}

