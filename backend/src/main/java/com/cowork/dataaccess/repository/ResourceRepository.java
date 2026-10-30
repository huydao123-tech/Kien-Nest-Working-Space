package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.Resource;
import com.cowork.dataaccess.entity.ResourceType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceRepository extends JpaRepository<Resource, UUID> {

    /** Đọc và khóa ghi hàng của vị trí đến hết giao dịch (SELECT ... FOR UPDATE) — dùng để chống đặt trùng. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Resource r where r.id = :id")
    Optional<Resource> findByIdForUpdate(@Param("id") UUID id);

    /** Các vị trí đang khai thác và đủ sức chứa. */
    @Query("""
            select r from Resource r
            where r.type = :type
              and r.status = com.cowork.dataaccess.entity.ResourceStatus.ACTIVE
              and r.capacity >= :minCapacity
            order by r.code
            """)
    List<Resource> findBookable(@Param("type") ResourceType type, @Param("minCapacity") int minCapacity);

    List<Resource> findAllByOrderByCodeAsc();
}
