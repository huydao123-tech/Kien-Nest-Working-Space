package com.cowork.business.service;

import com.cowork.business.exception.DomainException;
import com.cowork.config.PricingProperties;
import com.cowork.dataaccess.entity.DayType;
import com.cowork.dataaccess.entity.Money;
import com.cowork.dataaccess.entity.PriceRule;
import com.cowork.dataaccess.entity.ResourceType;
import com.cowork.dataaccess.entity.TimeRange;
import com.cowork.dataaccess.repository.PriceRuleRepository;
import com.cowork.dto.PriceQuote;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Tính tiền theo block giờ, dùng cho đặt chỗ, gia hạn và phụ phí quá giờ.
 * <ul>
 *   <li>BR-03: thời lượng làm tròn lên block gần nhất (mặc định 30 phút).</li>
 *   <li>BR-04: mỗi block lấy đơn giá của khung giờ chứa thời điểm bắt đầu block,
 *       nên đơn vắt qua cao điểm / thấp điểm được tính đúng từng phần.</li>
 *   <li>BR-06: phụ phí quá giờ = giá các block vượt × hệ số phụ thu.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PricingService {

    private final PriceRuleRepository priceRules;
    private final PricingProperties properties;

    public PricingService(PriceRuleRepository priceRules, PricingProperties properties) {
        this.priceRules = priceRules;
        this.properties = properties;
    }

    public PriceQuote quote(ResourceType type, TimeRange range) {
        List<PriceRule> rules = priceRules.findByResourceType(type);
        int blockMinutes = properties.blockMinutes();
        int blocks = (int) Math.ceilDiv(range.duration().toSeconds(), blockMinutes * 60L);

        Money total = Money.ZERO;
        LocalDateTime blockStart = range.startAt();
        for (int i = 0; i < blocks; i++) {
            PriceRule rule = findRule(rules, type, blockStart);
            total = total.plus(Money.vnd(rule.getPricePerBlock()));
            blockStart = blockStart.plusMinutes(blockMinutes);
        }
        return new PriceQuote(blocks, blockMinutes, total);
    }

    public Money overstaySurcharge(ResourceType type, TimeRange overstay) {
        return quote(type, overstay).total().multiply(properties.overstayMultiplier());
    }

    /** Tìm mức giá áp dụng cho loại chỗ {@code type} tại thời điểm {@code at}. */
    private static PriceRule findRule(List<PriceRule> rules, ResourceType type, LocalDateTime at) {
        int minuteOfDay = at.getHour() * 60 + at.getMinute();
        DayType dayType = DayType.of(at.getDayOfWeek());
        for (PriceRule rule : rules) {
            if (rule.getResourceType() == type
                    && rule.getDayType() == dayType
                    && minuteOfDay >= rule.getStartMinute()
                    && minuteOfDay < rule.getEndMinute()) {
                return rule;
            }
        }
        throw new DomainException("NO_PRICE_RULE", "Chưa cấu hình giá cho " + type + " tại thời điểm " + at);
    }
}
