package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.MembershipPass;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MembershipPassRepository extends JpaRepository<MembershipPass, Long> {

    /** FR-AUTH-05: mọi gói của khách cho trang hồ sơ, hết hạn sớm nhất trước; nạp kèm gói gốc để lấy tên. */
    @EntityGraph(attributePaths = "plan", type = EntityGraph.EntityGraphType.LOAD)
    List<MembershipPass> findByCustomerIdOrderByExpiresAtAscIdAsc(Long customerId);

    /**
     * BR-13: gói còn dùng được của khách (ACTIVE, còn phút, chưa hết hạn tại {@code at}), hết hạn sớm nhất xếp trước.
     * Khóa ghi đến hết giao dịch để hai đơn không cùng trừ một số phút.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from MembershipPass p
            where p.customer.id = :customerId
              and p.status = com.cowork.dataaccess.enums.MembershipPassStatus.ACTIVE
              and p.remainingMinutes > 0
              and p.expiresAt > :at
            order by p.expiresAt asc, p.id asc
            """)
    List<MembershipPass> findUsableForUpdate(@Param("customerId") Long customerId, @Param("at") LocalDateTime at);

    /** Khóa ghi một gói — dùng khi trả lại phút. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from MembershipPass p where p.id = :id")
    Optional<MembershipPass> findByIdForUpdate(@Param("id") Long id);
}
