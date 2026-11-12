package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ZoneRepository extends JpaRepository<Zone, Long> {

    List<Zone> findAllByOrderByIdAsc();
}
