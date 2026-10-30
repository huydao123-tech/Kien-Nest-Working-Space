package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.BookingStatus;
import com.cowork.dataaccess.entity.TimeRange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByQrToken(String qrToken);

    /** BR-01: có đơn nào (khác {@code excludeBookingId}) đang chiếm vị trí trong khoảng {@code range} không. */
    default boolean existsOccupying(UUID resourceId, TimeRange range, UUID excludeBookingId) {
        // "id <> null" trong SQL luôn cho kết quả UNKNOWN, nên khi không loại trừ đơn nào thì dùng một UUID giả.
        UUID exclude = excludeBookingId == null ? new UUID(0, 0) : excludeBookingId;
        return existsOverlap(resourceId, range.startAt(), range.endAt(), BookingStatus.SLOT_OCCUPYING, exclude);
    }

    /** Trong các vị trí {@code resourceIds}, những vị trí nào đã có đơn chiếm khoảng {@code range}. */
    default Set<UUID> findOccupiedResourceIds(Collection<UUID> resourceIds, TimeRange range) {
        if (resourceIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(findOccupied(resourceIds, range.startAt(), range.endAt(), BookingStatus.SLOT_OCCUPYING));
    }

    /** Đơn đang giữ chỗ đã quá hạn — cho job giải phóng chỗ. */
    default List<UUID> findHeldIdsExpiredAt(LocalDateTime now) {
        return findIdsByStatusAndHoldExpiredAt(BookingStatus.HELD, now);
    }

    /** Đơn đang check-in đã quá giờ kết thúc — cho job đánh dấu quá giờ. */
    default List<UUID> findCheckedInIdsEndedBefore(LocalDateTime threshold) {
        return findIdsByStatusAndEndBefore(BookingStatus.CHECKED_IN, threshold);
    }

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
    List<UUID> findOccupied(@Param("resourceIds") Collection<UUID> resourceIds,
                            @Param("startAt") LocalDateTime startAt,
                            @Param("endAt") LocalDateTime endAt,
                            @Param("statuses") Collection<BookingStatus> statuses);

    @Query("select b.id from Booking b where b.status = :status and b.holdExpiresAt <= :now")
    List<UUID> findIdsByStatusAndHoldExpiredAt(@Param("status") BookingStatus status, @Param("now") LocalDateTime now);

    @Query("select b.id from Booking b where b.status = :status and b.period.endAt < :threshold")
    List<UUID> findIdsByStatusAndEndBefore(@Param("status") BookingStatus status, @Param("threshold") LocalDateTime threshold);
}
