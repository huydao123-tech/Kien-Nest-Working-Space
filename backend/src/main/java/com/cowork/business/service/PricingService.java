package com.cowork.business.service;

import com.cowork.config.PricingProperties;
import com.cowork.dataaccess.entity.Money;
import com.cowork.dataaccess.entity.ResourceType;
import com.cowork.dataaccess.entity.TimeRange;
import com.cowork.dataaccess.repository.PriceRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tính giá cho các service khác (đặt chỗ, gia hạn, phụ phí quá giờ). */
@Service
@Transactional(readOnly = true)
public class PricingService {

    private final PriceRuleRepository priceRules;
    private final PricingProperties properties;
    private final PricingPolicy policy;

    public PricingService(PriceRuleRepository priceRules, PricingProperties properties) {
        this.priceRules = priceRules;
        this.properties = properties;
        this.policy = new PricingPolicy(properties.blockMinutes());
    }

    public PriceQuote quote(ResourceType type, TimeRange range) {
        return policy.quote(type, range, priceRules.findByResourceType(type));
    }

    public Money overstaySurcharge(ResourceType type, TimeRange overstay) {
        return policy.surcharge(type, overstay, properties.overstayMultiplier(), priceRules.findByResourceType(type));
    }
}
