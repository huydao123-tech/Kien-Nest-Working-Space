package com.cowork.presentation.controller;

import com.cowork.business.service.AvailabilityService;
import com.cowork.dataaccess.enums.ResourceTypeCode;
import com.cowork.dto.AvailabilityDtos.AmenityResponse;
import com.cowork.dto.AvailabilityDtos.AvailableResource;
import com.cowork.dto.AvailabilityDtos.BranchResponse;
import com.cowork.dto.AvailabilityDtos.FloorPlanResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/** UC02: chọn chi nhánh → chọn tiện ích lọc → tìm chỗ trống hoặc xem sơ đồ mặt bằng. */
@RestController
class AvailabilityController {

    private final AvailabilityService availabilityService;

    AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping("/api/branches")
    List<BranchResponse> branches() {
        return availabilityService.branches();
    }

    @GetMapping("/api/amenities")
    List<AmenityResponse> amenities() {
        return availabilityService.amenities();
    }

    /** {@code amenities} nhận cả {@code ?amenities=QUIET,POWER_OUTLET} lẫn {@code ?amenities=QUIET&amenities=POWER_OUTLET}. */
    @GetMapping("/api/availability")
    List<AvailableResource> search(@RequestParam Long branchId,
                                   @RequestParam(required = false) ResourceTypeCode type,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
                                   @RequestParam(defaultValue = "1") int partySize,
                                   @RequestParam(required = false) List<String> amenities) {
        return availabilityService.search(branchId, type, startAt, endAt, partySize, amenities);
    }

    @GetMapping("/api/branches/{branchId}/floor-plan")
    FloorPlanResponse floorPlan(@PathVariable Long branchId,
                                @RequestParam(required = false) ResourceTypeCode type,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt) {
        return availabilityService.floorPlan(branchId, type, startAt, endAt);
    }
}
