package com.cowork.booking.application;

import com.cowork.booking.domain.BookingRepository;
import com.cowork.pricing.application.PricingService;
import com.cowork.pricing.domain.PriceQuote;
import com.cowork.shared.domain.ResourceType;
import com.cowork.shared.domain.TimeRange;
import com.cowork.space.domain.Resource;
import com.cowork.space.domain.ResourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** UC02: tìm vị trí còn trống theo loại, khung giờ và số người. */
@Service
@Transactional(readOnly = true)
public class AvailabilityService {

    private final ResourceRepository resources;
    private final BookingRepository bookings;
    private final PricingService pricing;

    public AvailabilityService(ResourceRepository resources, BookingRepository bookings, PricingService pricing) {
        this.resources = resources;
        this.bookings = bookings;
        this.pricing = pricing;
    }

    public List<AvailableResource> search(ResourceType type, LocalDateTime startAt, LocalDateTime endAt, int partySize) {
        TimeRange range = new TimeRange(startAt, endAt);
        List<Resource> candidates = resources.findBookable(type, partySize);
        Set<UUID> occupied = bookings.findOccupiedResourceIds(candidates.stream().map(Resource::id).toList(), range);
        List<Resource> free = candidates.stream().filter(r -> !occupied.contains(r.id())).toList();
        if (free.isEmpty()) {
            return List.of();
        }
        PriceQuote quote = pricing.quote(type, range);
        return free.stream()
                .map(r -> new AvailableResource(r.id(), r.code(), r.name(), r.capacity(), quote))
                .toList();
    }

    public record AvailableResource(UUID resourceId, String code, String name, int capacity, PriceQuote quote) {
    }
}
