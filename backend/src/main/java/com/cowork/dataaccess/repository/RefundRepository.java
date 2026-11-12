package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.Refund;
import com.cowork.dataaccess.enums.RefundStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByPaymentBookingIdOrderByIdAsc(Long bookingId);

    /** Danh sách yêu cầu hoàn theo trạng thái, nạp kèm giao dịch và đơn để hiển thị mã đơn (tránh N+1). */
    @Query("""
            select r from Refund r
            join fetch r.payment p
            join fetch p.booking b
            where r.status = :status
            order by r.id asc
            """)
    List<Refund> findWithBookingByStatus(@Param("status") RefundStatus status);

    /** Đọc và khóa ghi yêu cầu hoàn (SELECT ... FOR UPDATE): hai người cùng bấm "đã hoàn" thì chỉ một người thành công. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refund r where r.id = :id")
    Optional<Refund> findByIdForUpdate(@Param("id") Long id);
}
