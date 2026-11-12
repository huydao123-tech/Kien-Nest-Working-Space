package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.Amenity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AmenityRepository extends JpaRepository<Amenity, Long> {

    List<Amenity> findAllByOrderByCodeAsc();

    List<Amenity> findByCodeIn(Collection<String> codes);

    boolean existsByCodeIgnoreCase(String code);
}
