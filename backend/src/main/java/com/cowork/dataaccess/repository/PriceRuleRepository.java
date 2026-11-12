package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.PriceRule;
import com.cowork.dataaccess.enums.ResourceTypeCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceRuleRepository extends JpaRepository<PriceRule, Long> {

    /** Bảng giá của một loại không gian, lọc theo mã loại ({@code resourceType.code}). */
    List<PriceRule> findByResourceTypeCode(ResourceTypeCode code);

    List<PriceRule> findAllByOrderByIdAsc();
}
