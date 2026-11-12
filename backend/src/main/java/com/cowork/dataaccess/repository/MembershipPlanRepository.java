package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.MembershipPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, Long> {

    List<MembershipPlan> findAllByOrderByIdAsc();
}
