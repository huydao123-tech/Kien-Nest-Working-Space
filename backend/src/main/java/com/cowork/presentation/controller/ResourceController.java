package com.cowork.presentation.controller;

import com.cowork.business.service.ResourceService;
import com.cowork.dataaccess.entity.Resource;
import com.cowork.dataaccess.entity.ResourceStatus;
import com.cowork.dataaccess.entity.ResourceType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/resources")
class ResourceController {

    private final ResourceService resourceService;

    ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping
    List<ResourceResponse> list() {
        return resourceService.list().stream().map(ResourceResponse::from).toList();
    }

    @PostMapping("/{id}/maintenance")
    ResourceResponse markMaintenance(@PathVariable UUID id) {
        return ResourceResponse.from(resourceService.markMaintenance(id));
    }

    @PostMapping("/{id}/reactivate")
    ResourceResponse reactivate(@PathVariable UUID id) {
        return ResourceResponse.from(resourceService.reactivate(id));
    }

    record ResourceResponse(UUID id, String code, String name, ResourceType type, int capacity, ResourceStatus status) {

        static ResourceResponse from(Resource r) {
            return new ResourceResponse(r.getId(), r.getCode(), r.getName(), r.getType(), r.getCapacity(), r.getStatus());
        }
    }
}
