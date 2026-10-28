package com.cowork.pricing.domain;

import com.cowork.shared.domain.DomainException;
import com.cowork.shared.domain.Money;
import com.cowork.shared.domain.ResourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Đơn giá một block giờ cho một loại không gian trong một khung giờ của ngày (cao điểm / thấp điểm).
 * Khung giờ tính bằng phút trong ngày, nửa mở [startMinute, endMinute).
 */
@Entity
@Table(name = "price_rule")
public class PriceRule {

    private static final int MINUTES_PER_DAY = 24 * 60;

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

    public static PriceRule of(ResourceType type, DayType dayType, int startMinute, int endMinute, Money pricePerBlock, String label) {
        if (startMinute < 0 || endMinute > MINUTES_PER_DAY || startMinute >= endMinute) {
            throw new DomainException("INVALID_PRICE_WINDOW", "Khung giờ giá không hợp lệ");
        }
        PriceRule rule = new PriceRule();
        rule.id = UUID.randomUUID();
        rule.resourceType = type;
        rule.dayType = dayType;
        rule.startMinute = startMinute;
        rule.endMinute = endMinute;
        rule.pricePerBlock = pricePerBlock.amount();
        rule.label = label;
        return rule;
    }

    public boolean appliesTo(ResourceType type, LocalDateTime at) {
        int minuteOfDay = at.getHour() * 60 + at.getMinute();
        return type == resourceType
                && dayType == DayType.of(at.getDayOfWeek())
                && minuteOfDay >= startMinute
                && minuteOfDay < endMinute;
    }

    public Money pricePerBlock() {
        return Money.vnd(pricePerBlock);
    }

    public ResourceType resourceType() {
        return resourceType;
    }

    public String label() {
        return label;
    }
}
