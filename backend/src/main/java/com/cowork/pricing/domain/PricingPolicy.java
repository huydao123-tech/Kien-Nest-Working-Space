package com.cowork.pricing.domain;

import com.cowork.shared.domain.DomainException;
import com.cowork.shared.domain.Money;
import com.cowork.shared.domain.ResourceType;
import com.cowork.shared.domain.TimeRange;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Domain service tính tiền theo block giờ.
 * <ul>
 *   <li>BR-03: thời lượng làm tròn lên block gần nhất.</li>
 *   <li>BR-04: mỗi block lấy đơn giá của khung giờ chứa thời điểm bắt đầu block,
 *       nên đơn vắt qua cao điểm/thấp điểm được tính đúng từng phần.</li>
 *   <li>BR-06: phụ phí quá giờ = giá các block vượt × hệ số phụ thu.</li>
 * </ul>
 */
public final class PricingPolicy {

    private final int blockMinutes;

    public PricingPolicy(int blockMinutes) {
        if (blockMinutes <= 0) {
            throw new IllegalArgumentException("blockMinutes must be positive");
        }
        this.blockMinutes = blockMinutes;
    }

    public int blocksFor(Duration duration) {
        return (int) Math.ceilDiv(duration.toSeconds(), blockMinutes * 60L);
    }

    public PriceQuote quote(ResourceType type, TimeRange range, List<PriceRule> rules) {
        int blocks = blocksFor(range.duration());
        Money total = Money.ZERO;
        LocalDateTime cursor = range.startAt();
        for (int i = 0; i < blocks; i++) {
            total = total.plus(ruleAt(type, cursor, rules).pricePerBlock());
            cursor = cursor.plusMinutes(blockMinutes);
        }
        return new PriceQuote(blocks, blockMinutes, total);
    }

    public Money surcharge(ResourceType type, TimeRange overstay, BigDecimal multiplier, List<PriceRule> rules) {
        return quote(type, overstay, rules).total().multiply(multiplier);
    }

    private static PriceRule ruleAt(ResourceType type, LocalDateTime at, List<PriceRule> rules) {
        return rules.stream()
                .filter(rule -> rule.appliesTo(type, at))
                .findFirst()
                .orElseThrow(() -> new DomainException("NO_PRICE_RULE",
                        "Chưa cấu hình giá cho " + type + " tại thời điểm " + at));
    }
}
