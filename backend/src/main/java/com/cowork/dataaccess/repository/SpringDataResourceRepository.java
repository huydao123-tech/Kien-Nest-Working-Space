package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.Resource;
import com.cowork.dataaccess.entity.ResourceStatus;
import com.cowork.dataaccess.entity.ResourceType;
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
