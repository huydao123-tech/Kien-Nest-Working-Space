package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.SpaceResource;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SpaceResourceRepository extends JpaRepository<SpaceResource, Long> {

    /** Đọc và khóa ghi hàng của vị trí đến hết giao dịch (SELECT ... FOR UPDATE) — dùng để chống đặt trùng. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SpaceResource r where r.id = :id")
    Optional<SpaceResource> findByIdForUpdate(@Param("id") Long id);

    /**
     * Các vị trí của một chi nhánh (đi qua zone → branch), trừ vị trí đã ngưng khai thác.
     * Nạp kèm khu vực và tiện ích trong cùng câu truy vấn để không phát sinh thêm câu SELECT cho từng vị trí.
     */
    @Query("""
            select distinct r from SpaceResource r
            join fetch r.zone z
            left join fetch r.amenities ra
            left join fetch ra.amenity
            where z.branch.id = :branchId
              and r.status <> com.cowork.dataaccess.enums.ResourceStatus.INACTIVE
            order by z.floor, z.name, r.code
            """)
    List<SpaceResource> findInBranch(@Param("branchId") Long branchId);

    List<SpaceResource> findAllByOrderByCodeAsc();

    boolean existsByCodeIgnoreCase(String code);
}
