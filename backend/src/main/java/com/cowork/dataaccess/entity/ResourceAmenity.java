package com.cowork.dataaccess.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Tiện ích được trang bị cho vị trí và số lượng. Bảng: {@code resource_amenity}.
 * Bảng nối SpaceResource ↔ Amenity nhưng có thêm cột {@code quantity}, nên là entity riêng thay vì {@code @ManyToMany}.
 */
@Entity
@Table(name = "resource_amenity",
        uniqueConstraints = @UniqueConstraint(name = "uq_resource_amenity", columnNames = {"resource_id", "amenity_id"}))
public class ResourceAmenity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private SpaceResource resource;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "amenity_id", nullable = false)
    private Amenity amenity;

    @Column(nullable = false)
    private int quantity = 1;

    public ResourceAmenity() {
    }

    public ResourceAmenity(Amenity amenity, int quantity) {
        this.amenity = amenity;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SpaceResource getResource() {
        return resource;
    }

    public void setResource(SpaceResource resource) {
        this.resource = resource;
    }

    public Amenity getAmenity() {
        return amenity;
    }

    public void setAmenity(Amenity amenity) {
        this.amenity = amenity;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
