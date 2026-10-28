package com.cowork.pricing.infrastructure;

import com.cowork.pricing.domain.PriceRule;
import com.cowork.shared.domain.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataPriceRuleRepository extends JpaRepository<PriceRule, UUID> {

    List<PriceRule> findByResourceType(ResourceType type);
}
