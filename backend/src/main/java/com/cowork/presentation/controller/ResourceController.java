package com.cowork.presentation.controller;

import com.cowork.business.service.ResourceService;
import com.cowork.dataaccess.entity.SpaceResource;
import com.cowork.dataaccess.enums.ResourceStatus;
import com.cowork.dataaccess.enums.ResourceTypeCode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/resources")
class ResourceController {

    private final ResourceService resourceService;

    ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping
    @PreAuthorize(Roles.ALL_STAFF)
    List<ResourceResponse> list() {
        return resourceService.list().stream().map(ResourceResponse::from).toList();
    }

    @PostMapping("/{id}/maintenance")
    @PreAuthorize(Roles.MAINTENANCE)
    ResourceResponse markMaintenance(@PathVariable Long id) {
        return ResourceResponse.from(resourceService.markMaintenance(id));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize(Roles.MAINTENANCE)
    ResourceResponse reactivate(@PathVariable Long id) {
        return ResourceResponse.from(resourceService.reactivate(id));
    }

    record ResourceResponse(Long id, String code, String name, ResourceTypeCode type, int capacity, ResourceStatus status) {

        static ResourceResponse from(SpaceResource r) {
            return new ResourceResponse(r.getId(), r.getCode(), r.getName(), r.getType(), r.getCapacity(), r.getStatus());
        }
    }
}
