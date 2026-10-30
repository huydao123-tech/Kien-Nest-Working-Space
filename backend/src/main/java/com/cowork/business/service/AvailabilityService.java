package com.cowork.business.service;

import com.cowork.business.exception.DomainException;
import com.cowork.dataaccess.entity.Resource;
import com.cowork.dataaccess.entity.ResourceType;
import com.cowork.dataaccess.entity.TimeRange;
import com.cowork.dataaccess.repository.BookingRepository;
import com.cowork.dataaccess.repository.ResourceRepository;
import com.cowork.dto.PriceQuote;
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
        if (!endAt.isAfter(startAt)) {
            throw new DomainException("INVALID_TIME_RANGE", "Giờ kết thúc phải sau giờ bắt đầu");
        }
        TimeRange range = new TimeRange(startAt, endAt);

        List<Resource> candidates = resources.findBookable(type, partySize);
        Set<UUID> occupied = bookings.findOccupiedResourceIds(candidates.stream().map(Resource::getId).toList(), range);
        List<Resource> free = candidates.stream().filter(r -> !occupied.contains(r.getId())).toList();
        if (free.isEmpty()) {
            return List.of();
        }
        PriceQuote quote = pricing.quote(type, range);
        return free.stream()
                .map(r -> new AvailableResource(r.getId(), r.getCode(), r.getName(), r.getCapacity(), quote))
                .toList();
    }

    public record AvailableResource(UUID resourceId, String code, String name, int capacity, PriceQuote quote) {
    }
}
