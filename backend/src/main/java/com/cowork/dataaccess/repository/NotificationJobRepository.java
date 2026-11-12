package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.NotificationJob;
import com.cowork.dataaccess.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface NotificationJobRepository extends JpaRepository<NotificationJob, Long> {

    /** Chống gửi trùng: đơn đã có thông báo loại {@code type} cho đúng mốc {@code sendAt} chưa. */
    boolean existsByBookingIdAndTypeAndSendAt(Long bookingId, NotificationType type, LocalDateTime sendAt);
}
