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

/** Voucher áp dụng cho loại không gian nào. Bảng: {@code voucher_resource_type}, khóa ghép (voucher_id, resource_type_id). */
@Entity
@Table(name = "voucher_resource_type")
@IdClass(VoucherResourceType.Key.class)
public class VoucherResourceType {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_type_id", nullable = false)
    private ResourceType resourceType;

    public VoucherResourceType() {
    }

    public VoucherResourceType(Voucher voucher, ResourceType resourceType) {
        this.voucher = voucher;
        this.resourceType = resourceType;
    }

    public Voucher getVoucher() {
        return voucher;
    }

    public void setVoucher(Voucher voucher) {
        this.voucher = voucher;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public void setResourceType(ResourceType resourceType) {
        this.resourceType = resourceType;
    }

    /** Khóa chính ghép: tên field trùng với hai field {@code @Id} ở trên, mỗi field giữ id của bảng được tham chiếu. */
    public static class Key implements Serializable {

        private Long voucher;
        private Long resourceType;

        public Key() {
        }

        public Key(Long voucher, Long resourceType) {
            this.voucher = voucher;
            this.resourceType = resourceType;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other && Objects.equals(voucher, other.voucher) && Objects.equals(resourceType, other.resourceType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(voucher, resourceType);
        }
    }
}
