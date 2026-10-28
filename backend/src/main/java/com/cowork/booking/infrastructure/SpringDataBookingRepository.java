package com.cowork.booking.infrastructure;

import com.cowork.booking.domain.Booking;
import com.cowork.booking.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataBookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByQrToken(String qrToken);

    @Query("""
            select count(b) > 0 from Booking b
            where b.resourceId = :resourceId
              and b.status in :statuses
              and b.period.startAt < :endAt
              and b.period.endAt > :startAt
              and b.id <> :excludeId
            """)
    boolean existsOverlap(@Param("resourceId") UUID resourceId,
                          @Param("startAt") LocalDateTime startAt,
                          @Param("endAt") LocalDateTime endAt,
                          @Param("statuses") Collection<BookingStatus> statuses,
                          @Param("excludeId") UUID excludeId);

    @Query("""
            select distinct b.resourceId from Booking b
            where b.resourceId in :resourceIds
              and b.status in :statuses
              and b.period.startAt < :endAt
              and b.period.endAt > :startAt
            """)
    List<UUID> findOccupiedResourceIds(@Param("resourceIds") Collection<UUID> resourceIds,
                                       @Param("startAt") LocalDateTime startAt,
                                       @Param("endAt") LocalDateTime endAt,
                                       @Param("statuses") Collection<BookingStatus> statuses);

    @Query("select b.id from Booking b where b.status = :status and b.holdExpiresAt <= :now")
    List<UUID> findIdsByStatusAndHoldExpiredAt(@Param("status") BookingStatus status, @Param("now") LocalDateTime now);

    @Query("select b.id from Booking b where b.status = :status and b.period.endAt < :threshold")
    List<UUID> findIdsByStatusAndEndBefore(@Param("status") BookingStatus status, @Param("threshold") LocalDateTime threshold);
}
