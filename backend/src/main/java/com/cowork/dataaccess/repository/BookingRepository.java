package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.TimeRange;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface BookingRepository {

    Booking save(Booking booking);

    Optional<Booking> findById(UUID id);

    Optional<Booking> findByQrToken(String qrToken);

    /** BR-01: có đơn nào (khác {@code excludeBookingId}) đang chiếm vị trí trong khoảng {@code range} không. */
    boolean existsOccupying(UUID resourceId, TimeRange range, UUID excludeBookingId);

    Set<UUID> findOccupiedResourceIds(Collection<UUID> resourceIds, TimeRange range);

    List<UUID> findHeldIdsExpiredAt(LocalDateTime now);

    List<UUID> findCheckedInIdsEndedBefore(LocalDateTime threshold);
}
