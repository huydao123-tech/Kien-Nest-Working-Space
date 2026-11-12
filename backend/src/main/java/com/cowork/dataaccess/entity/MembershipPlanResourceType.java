package com.cowork.dataaccess.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/** Gói thành viên được dùng cho loại không gian nào. Bảng: {@code membership_plan_resource_type}, khóa ghép (plan_id, resource_type_id). */
@Entity
@Table(name = "membership_plan_resource_type")
@IdClass(MembershipPlanResourceType.Key.class)
public class MembershipPlanResourceType {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private MembershipPlan plan;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_type_id", nullable = false)
    private ResourceType resourceType;

    public MembershipPlanResourceType() {
    }

    public MembershipPlanResourceType(MembershipPlan plan, ResourceType resourceType) {
        this.plan = plan;
        this.resourceType = resourceType;
    }

    public MembershipPlan getPlan() {
        return plan;
    }

    public void setPlan(MembershipPlan plan) {
        this.plan = plan;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public void setResourceType(ResourceType resourceType) {
        this.resourceType = resourceType;
    }

    /** Khóa chính ghép: tên field trùng với hai field {@code @Id} ở trên, mỗi field giữ id của bảng được tham chiếu. */
    public static class Key implements Serializable {

        private Long plan;
        private Long resourceType;

        public Key() {
        }

        public Key(Long plan, Long resourceType) {
            this.plan = plan;
            this.resourceType = resourceType;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other && Objects.equals(plan, other.plan) && Objects.equals(resourceType, other.resourceType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(plan, resourceType);
        }
    }
}
