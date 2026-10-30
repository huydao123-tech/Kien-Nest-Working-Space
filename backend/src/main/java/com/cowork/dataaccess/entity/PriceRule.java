package com.cowork.dataaccess.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Đơn giá một block giờ cho một loại không gian trong một khung giờ của ngày (cao điểm / thấp điểm).
 * Khung giờ tính bằng phút trong ngày, nửa mở [startMinute, endMinute).
 */
@Entity
@Table(name = "price_rule")
public class PriceRule {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResourceType resourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DayType dayType;

    private int startMinute;

    private int endMinute;

    private long pricePerBlock;

    @Column(nullable = false, length = 50)
    private String label;

    protected PriceRule() {
    }

    public PriceRule(ResourceType resourceType, DayType dayType, int startMinute, int endMinute, long pricePerBlock, String label) {
        this.id = UUID.randomUUID();
        this.resourceType = resourceType;
        this.dayType = dayType;
        this.startMinute = startMinute;
        this.endMinute = endMinute;
        this.pricePerBlock = pricePerBlock;
        this.label = label;
    }

    public UUID getId() {
        return id;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public void setResourceType(ResourceType resourceType) {
        this.resourceType = resourceType;
    }

    public DayType getDayType() {
        return dayType;
    }

    public void setDayType(DayType dayType) {
        this.dayType = dayType;
    }

    public int getStartMinute() {
        return startMinute;
    }

    public void setStartMinute(int startMinute) {
        this.startMinute = startMinute;
    }

    public int getEndMinute() {
        return endMinute;
    }

    public void setEndMinute(int endMinute) {
        this.endMinute = endMinute;
    }

    public long getPricePerBlock() {
        return pricePerBlock;
    }

    public void setPricePerBlock(long pricePerBlock) {
        this.pricePerBlock = pricePerBlock;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
