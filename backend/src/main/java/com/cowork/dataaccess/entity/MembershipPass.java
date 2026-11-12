package com.cowork.dataaccess.entity;

import com.cowork.dataaccess.enums.MembershipPassStatus;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Gói thành viên khách đã mua, quản lý số phút còn lại. Bảng: {@code membership_pass}. */
@Entity
@Table(name = "membership_pass")
public class MembershipPass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private MembershipPlan plan;

    @Column(name = "remaining_minutes", nullable = false)
    private int remainingMinutes;

    @Column(name = "purchased_at", nullable = false)
    private LocalDateTime purchasedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipPassStatus status = MembershipPassStatus.ACTIVE;

    @Version
    private Long version;

    @OneToMany(mappedBy = "pass")
    private List<MembershipUsage> usages = new ArrayList<>();

    public MembershipPass() {
    }

    public MembershipPass(Customer customer, MembershipPlan plan, int totalMinutes, LocalDateTime purchasedAt, LocalDateTime expiresAt) {
        this.customer = customer;
        this.plan = plan;
        this.remainingMinutes = totalMinutes;
        this.purchasedAt = purchasedAt;
        this.expiresAt = expiresAt;
        this.status = MembershipPassStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public MembershipPlan getPlan() {
        return plan;
    }

    public void setPlan(MembershipPlan plan) {
        this.plan = plan;
    }

    public int getRemainingMinutes() {
        return remainingMinutes;
    }

    public void setRemainingMinutes(int remainingMinutes) {
        this.remainingMinutes = remainingMinutes;
    }

    public LocalDateTime getPurchasedAt() {
        return purchasedAt;
    }

    public void setPurchasedAt(LocalDateTime purchasedAt) {
        this.purchasedAt = purchasedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public MembershipPassStatus getStatus() {
        return status;
    }

    public void setStatus(MembershipPassStatus status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public List<MembershipUsage> getUsages() {
        return usages;
    }
}
