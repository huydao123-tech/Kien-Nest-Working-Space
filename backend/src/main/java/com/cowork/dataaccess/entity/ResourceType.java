package com.cowork.dataaccess.entity;

import com.cowork.dataaccess.enums.ResourceTypeCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Danh mục loại không gian. Bảng: {@code resource_type}.
 * Cột {@code code} lưu tên enum {@link ResourceTypeCode}; code nghiệp vụ (tính giá, tìm chỗ) so sánh theo enum này.
 */
@Entity
@Table(name = "resource_type")
public class ResourceType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 20)
    private ResourceTypeCode code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    public ResourceType() {
    }

    public ResourceType(ResourceTypeCode code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ResourceTypeCode getCode() {
        return code;
    }

    public void setCode(ResourceTypeCode code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
