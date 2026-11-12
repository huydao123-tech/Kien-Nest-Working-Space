package com.cowork.dataaccess.entity;

import com.cowork.dataaccess.enums.MembershipPlanStatus;
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

import java.util.ArrayList;
import java.util.List;

/** Gói thành viên đang bán. Bảng: {@code membership_plan}. */
@Entity
@Table(name = "membership_plan")
public class MembershipPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "total_minutes", nullable = false)
    private int totalMinutes;

    @Column(name = "valid_days", nullable = false)
    private int validDays;

    @Column(nullable = false)
    private long price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipPlanStatus status = MembershipPlanStatus.ACTIVE;

    /** Loại không gian được dùng gói thành viên. */
    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MembershipPlanResourceType> resourceTypes = new ArrayList<>();

    public MembershipPlan() {
    }

    public MembershipPlan(String name, int totalMinutes, int validDays, long price) {
        this.name = name;
        this.totalMinutes = totalMinutes;
        this.validDays = validDays;
        this.price = price;
        this.status = MembershipPlanStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getTotalMinutes() {
        return totalMinutes;
    }

    public void setTotalMinutes(int totalMinutes) {
        this.totalMinutes = totalMinutes;
    }

    public int getValidDays() {
        return validDays;
    }

    public void setValidDays(int validDays) {
        this.validDays = validDays;
    }

    public long getPrice() {
        return price;
    }

    public void setPrice(long price) {
        this.price = price;
    }

    public MembershipPlanStatus getStatus() {
        return status;
    }

    public void setStatus(MembershipPlanStatus status) {
        this.status = status;
    }

    public List<MembershipPlanResourceType> getResourceTypes() {
        return resourceTypes;
    }

    public void setResourceTypes(List<MembershipPlanResourceType> resourceTypes) {
        this.resourceTypes = resourceTypes;
    }
}
