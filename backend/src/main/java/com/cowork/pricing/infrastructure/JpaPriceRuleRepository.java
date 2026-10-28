package com.cowork.pricing.infrastructure;

import com.cowork.pricing.domain.PriceRule;
import com.cowork.pricing.domain.PriceRuleRepository;
import com.cowork.shared.domain.ResourceType;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
class JpaPriceRuleRepository implements PriceRuleRepository {

    private final SpringDataPriceRuleRepository jpa;

    JpaPriceRuleRepository(SpringDataPriceRuleRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<PriceRule> findByResourceType(ResourceType type) {
        return jpa.findByResourceType(type);
    }

    @Override
    public PriceRule save(PriceRule rule) {
        return jpa.save(rule);
    }
}
