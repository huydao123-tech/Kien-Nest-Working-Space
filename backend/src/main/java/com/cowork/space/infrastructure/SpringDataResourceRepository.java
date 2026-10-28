package com.cowork.space.infrastructure;

import com.cowork.shared.domain.ResourceType;
import com.cowork.space.domain.Resource;
import com.cowork.space.domain.ResourceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataResourceRepository extends JpaRepository<Resource, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Resource r where r.id = :id")
    Optional<Resource> lockById(@Param("id") UUID id);

    List<Resource> findByTypeAndStatusAndCapacityGreaterThanEqualOrderByCode(
            ResourceType type, ResourceStatus status, int capacity);
}
