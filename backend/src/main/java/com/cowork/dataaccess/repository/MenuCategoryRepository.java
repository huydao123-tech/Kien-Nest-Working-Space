package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.MenuCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuCategoryRepository extends JpaRepository<MenuCategory, Long> {

    List<MenuCategory> findAllByOrderBySortOrderAscIdAsc();

    boolean existsByNameIgnoreCase(String name);
}
