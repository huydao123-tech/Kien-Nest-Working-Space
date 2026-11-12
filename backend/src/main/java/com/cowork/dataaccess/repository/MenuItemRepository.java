package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findAllByOrderByIdAsc();
}
