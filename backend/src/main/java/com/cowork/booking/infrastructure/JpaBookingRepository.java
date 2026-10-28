package com.cowork.booking.infrastructure;

import com.cowork.booking.domain.Booking;
import com.cowork.booking.domain.BookingRepository;
import com.cowork.booking.domain.BookingStatus;
import com.cowork.shared.domain.TimeRange;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
class JpaBookingRepository implements BookingRepository {

    /** Dùng khi không cần loại trừ đơn nào: {@code id <> null} trong SQL luôn cho kết quả UNKNOWN. */
    private static final UUID NO_BOOKING = new UUID(0, 0);

    private final SpringDataBookingRepository jpa;

    JpaBookingRepository(SpringDataBookingRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Booking save(Booking booking) {
        return jpa.save(booking);
    }

    @Override
    public Optional<Booking> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Booking> findByQrToken(String qrToken) {
        return jpa.findByQrToken(qrToken);
    }

    @Override
    public boolean existsOccupying(UUID resourceId, TimeRange range, UUID excludeBookingId) {
        return jpa.existsOverlap(resourceId, range.startAt(), range.endAt(), BookingStatus.SLOT_OCCUPYING,
                excludeBookingId == null ? NO_BOOKING : excludeBookingId);
    }

    @Override
    public Set<UUID> findOccupiedResourceIds(Collection<UUID> resourceIds, TimeRange range) {
        if (resourceIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jpa.findOccupiedResourceIds(resourceIds, range.startAt(), range.endAt(), BookingStatus.SLOT_OCCUPYING));
    }

    @Override
    public List<UUID> findHeldIdsExpiredAt(LocalDateTime now) {
        return jpa.findIdsByStatusAndHoldExpiredAt(BookingStatus.HELD, now);
    }

    @Override
    public List<UUID> findCheckedInIdsEndedBefore(LocalDateTime threshold) {
        return jpa.findIdsByStatusAndEndBefore(BookingStatus.CHECKED_IN, threshold);
    }
}
