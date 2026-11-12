package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.BookingAddon;
import com.cowork.dataaccess.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface BookingAddonRepository extends JpaRepository<BookingAddon, Long> {

    /**
     * FR-BOOK-02: các đơn đang chiếm chỗ (trừ đơn {@code excludeId}) có giữ add-on {@code addonId}
     * trong khoảng giao với [startAt, endAt). Chỉ lấy 3 cột cần để tính lúc dùng nhiều nhất.
     */
    @Query("""
            select b.period.startAt as startAt, b.period.endAt as endAt, ba.quantity as quantity
            from BookingAddon ba join ba.booking b
            where ba.addon.id = :addonId
              and b.status in :statuses
              and b.period.startAt < :endAt
              and b.period.endAt > :startAt
              and b.id <> :excludeId
            """)
    List<Reservation> findReservations(@Param("addonId") Long addonId,
                                       @Param("startAt") LocalDateTime startAt,
                                       @Param("endAt") LocalDateTime endAt,
                                       @Param("statuses") Collection<BookingStatus> statuses,
                                       @Param("excludeId") Long excludeId);

    /**
     * Add-on của các đơn (trừ đơn {@code excludeId}) có yêu cầu đổi chỗ đang chờ thanh toán: add-on được giữ cả ở
     * khung giờ mới (booking.md Prompt 2).
     */
    @Query("""
            select b.pendingStartAt as startAt, b.pendingEndAt as endAt, ba.quantity as quantity
            from BookingAddon ba join ba.booking b
            where ba.addon.id = :addonId
              and b.pendingExpiresAt is not null
              and b.pendingStartAt < :endAt
              and b.pendingEndAt > :startAt
              and b.id <> :excludeId
            """)
    List<Reservation> findPendingReservations(@Param("addonId") Long addonId,
                                              @Param("startAt") LocalDateTime startAt,
                                              @Param("endAt") LocalDateTime endAt,
                                              @Param("excludeId") Long excludeId);

    /** Một đơn đang giữ add-on: khung giờ của đơn và số lượng. */
    interface Reservation {

        LocalDateTime getStartAt();

        LocalDateTime getEndAt();

        int getQuantity();
    }
}
