package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.Branch;
import com.cowork.dataaccess.enums.BranchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findByStatusOrderByCodeAsc(BranchStatus status);

    List<Branch> findAllByOrderByCodeAsc();

    boolean existsByCodeIgnoreCase(String code);
}
