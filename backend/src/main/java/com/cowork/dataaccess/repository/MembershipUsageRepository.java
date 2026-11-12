package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.MembershipUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MembershipUsageRepository extends JpaRepository<MembershipUsage, Long> {

    List<MembershipUsage> findByBookingId(Long bookingId);
}
