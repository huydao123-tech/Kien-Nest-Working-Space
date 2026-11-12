package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.VoucherRedemption;
import com.cowork.dataaccess.enums.VoucherRedemptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface VoucherRedemptionRepository extends JpaRepository<VoucherRedemption, Long> {

    Optional<VoucherRedemption> findByBookingId(Long bookingId);

    /** Số lượt một khách đang giữ hoặc đã dùng một voucher — cho giới hạn lượt / khách (BR-12). */
    long countByVoucherIdAndCustomerIdAndStatusIn(Long voucherId, Long customerId, Collection<VoucherRedemptionStatus> statuses);
}
