package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.Staff;
import com.cowork.dataaccess.enums.StaffRole;
import com.cowork.dataaccess.enums.StaffStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long> {

    Optional<Staff> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<Staff> findAllByOrderByIdAsc();

    /** FR-SYS-02: nhân viên một vai trò, đang hoạt động, của một chi nhánh (ví dụ lễ tân nhận cảnh báo quá giờ). */
    List<Staff> findByBranchIdAndRoleAndStatusOrderByIdAsc(Long branchId, StaffRole role, StaffStatus status);
}
