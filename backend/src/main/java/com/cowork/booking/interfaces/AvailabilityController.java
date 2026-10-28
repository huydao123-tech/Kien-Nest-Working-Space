package com.cowork.booking.interfaces;

import com.cowork.booking.application.AvailabilityService;
import com.cowork.booking.application.AvailabilityService.AvailableResource;
import com.cowork.shared.domain.ResourceType;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
class AvailabilityController {

    private final AvailabilityService availabilityService;

    AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping("/api/availability")
    List<AvailableResource> search(@RequestParam ResourceType type,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
                                   @RequestParam(defaultValue = "1") int partySize) {
        return availabilityService.search(type, startAt, endAt, partySize);
    }
}
