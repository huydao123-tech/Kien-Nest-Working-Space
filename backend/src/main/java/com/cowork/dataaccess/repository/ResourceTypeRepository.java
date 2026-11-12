package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.enums.ResourceTypeCode;
import com.cowork.dataaccess.entity.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResourceTypeRepository extends JpaRepository<ResourceType, Long> {

    Optional<ResourceType> findByCode(ResourceTypeCode code);

    List<ResourceType> findAllByOrderByIdAsc();
}
