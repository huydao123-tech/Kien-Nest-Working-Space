package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.Voucher;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    /** Đọc và khóa ghi voucher đến hết giao dịch, để hai đơn cùng tranh lượt cuối không cùng thành công. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Voucher v where upper(v.code) = upper(:code)")
    Optional<Voucher> findByCodeForUpdate(@Param("code") String code);

    /** Khóa ghi voucher theo id — dùng khi trả lại lượt. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Voucher v where v.id = :id")
    Optional<Voucher> findByIdForUpdate(@Param("id") Long id);

    boolean existsByCodeIgnoreCase(String code);

    List<Voucher> findAllByOrderByIdAsc();
}
