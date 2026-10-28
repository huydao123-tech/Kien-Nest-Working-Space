package com.cowork.pricing.application;

import com.cowork.pricing.domain.PriceQuote;
import com.cowork.pricing.domain.PriceRuleRepository;
import com.cowork.pricing.domain.PricingPolicy;
import com.cowork.shared.domain.Money;
import com.cowork.shared.domain.ResourceType;
import com.cowork.shared.domain.TimeRange;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cổng vào của context Pricing cho các context khác. */
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
