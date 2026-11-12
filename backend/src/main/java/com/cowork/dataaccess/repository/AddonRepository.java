package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.Addon;
import com.cowork.dataaccess.enums.AddonStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AddonRepository extends JpaRepository<Addon, Long> {

    /** Đọc và khóa ghi dòng add-on đến hết giao dịch — hai đơn cùng lấy một add-on được xử lý lần lượt. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Addon a where a.id = :id")
    Optional<Addon> findByIdForUpdate(@Param("id") Long id);

    List<Addon> findByBranchIdAndStatusOrderByCodeAsc(Long branchId, AddonStatus status);

    List<Addon> findAllByOrderByIdAsc();

    boolean existsByBranchIdAndCodeIgnoreCase(Long branchId, String code);
}
