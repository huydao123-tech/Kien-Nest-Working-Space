package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.PriceRule;
import com.cowork.dataaccess.entity.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataPriceRuleRepository extends JpaRepository<PriceRule, UUID> {

    List<PriceRule> findByResourceType(ResourceType type);
}
