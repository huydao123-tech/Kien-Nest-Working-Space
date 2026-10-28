package com.cowork.pricing.domain;

import com.cowork.shared.domain.ResourceType;

import java.util.List;

public interface PriceRuleRepository {

    List<PriceRule> findByResourceType(ResourceType type);

    PriceRule save(PriceRule rule);
}
