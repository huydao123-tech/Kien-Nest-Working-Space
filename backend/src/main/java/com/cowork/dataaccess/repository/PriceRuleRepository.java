package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.PriceRule;
import com.cowork.dataaccess.entity.ResourceType;

import java.util.List;

public interface PriceRuleRepository {

    List<PriceRule> findByResourceType(ResourceType type);

    PriceRule save(PriceRule rule);
}
