package com.cowork.presentation.controller;

import com.cowork.business.service.CatalogService;
import com.cowork.dto.CatalogDtos.AmenityDetail;
import com.cowork.dto.CatalogDtos.AmenityRequest;
import com.cowork.dto.CatalogDtos.BranchDetail;
import com.cowork.dto.CatalogDtos.BranchRequest;
import com.cowork.dto.CatalogDtos.PriceRuleDetail;
import com.cowork.dto.CatalogDtos.PriceRuleRequest;
import com.cowork.dto.CatalogDtos.ResourceTypeDetail;
import com.cowork.dto.CatalogDtos.ResourceTypeRequest;
import com.cowork.dto.CatalogDtos.SpaceResourceDetail;
import com.cowork.dto.CatalogDtos.SpaceResourceRequest;
import com.cowork.dto.CatalogDtos.ZoneDetail;
import com.cowork.dto.CatalogDtos.ZoneRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** UC19, UC20: CRUD chi nhánh, khu vực, loại chỗ, vị trí, tiện ích, bảng giá. Quyền từng thao tác ghi trên hàm. */
@RestController
@RequestMapping("/api/admin")
class CatalogController {

    private final CatalogService catalog;

    CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    // ---------------- branch ----------------

    @GetMapping("/branches")
    @PreAuthorize(Roles.ALL_STAFF)
    List<BranchDetail> listBranches() {
        return catalog.listBranches();
    }

    @GetMapping("/branches/{id}")
    @PreAuthorize(Roles.ALL_STAFF)
    BranchDetail getBranch(@PathVariable Long id) {
        return catalog.getBranch(id);
    }

    @PostMapping("/branches")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.ADMIN)
    BranchDetail createBranch(@Valid @RequestBody BranchRequest request) {
        return catalog.createBranch(request);
    }

    @PutMapping("/branches/{id}")
    @PreAuthorize(Roles.ADMIN_MANAGER)
    BranchDetail updateBranch(@PathVariable Long id, @Valid @RequestBody BranchRequest request) {
        return catalog.updateBranch(id, request);
    }

    @DeleteMapping("/branches/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.ADMIN)
    void deleteBranch(@PathVariable Long id) {
        catalog.deleteBranch(id);
    }

    // ---------------- zone ----------------

    @GetMapping("/zones")
    @PreAuthorize(Roles.ALL_STAFF)
    List<ZoneDetail> listZones() {
        return catalog.listZones();
    }

    @GetMapping("/zones/{id}")
    @PreAuthorize(Roles.ALL_STAFF)
    ZoneDetail getZone(@PathVariable Long id) {
        return catalog.getZone(id);
    }

    @PostMapping("/zones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.ADMIN_MANAGER)
    ZoneDetail createZone(@Valid @RequestBody ZoneRequest request) {
        return catalog.createZone(request);
    }

    @PutMapping("/zones/{id}")
    @PreAuthorize(Roles.ADMIN_MANAGER)
    ZoneDetail updateZone(@PathVariable Long id, @Valid @RequestBody ZoneRequest request) {
        return catalog.updateZone(id, request);
    }

    @DeleteMapping("/zones/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.ADMIN_MANAGER)
    void deleteZone(@PathVariable Long id) {
        catalog.deleteZone(id);
    }

    // ---------------- resource_type ----------------

    @GetMapping("/resource-types")
    @PreAuthorize(Roles.ALL_STAFF)
    List<ResourceTypeDetail> listResourceTypes() {
        return catalog.listResourceTypes();
    }

    @GetMapping("/resource-types/{id}")
    @PreAuthorize(Roles.ALL_STAFF)
    ResourceTypeDetail getResourceType(@PathVariable Long id) {
        return catalog.getResourceType(id);
    }

    @PostMapping("/resource-types")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.ADMIN)
    ResourceTypeDetail createResourceType(@Valid @RequestBody ResourceTypeRequest request) {
        return catalog.createResourceType(request);
    }

    @PutMapping("/resource-types/{id}")
    @PreAuthorize(Roles.ADMIN)
    ResourceTypeDetail updateResourceType(@PathVariable Long id, @Valid @RequestBody ResourceTypeRequest request) {
        return catalog.updateResourceType(id, request);
    }

    @DeleteMapping("/resource-types/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.ADMIN)
    void deleteResourceType(@PathVariable Long id) {
        catalog.deleteResourceType(id);
    }

    // ---------------- space_resource ----------------

    @GetMapping("/resources")
    @PreAuthorize(Roles.ALL_STAFF)
    List<SpaceResourceDetail> listResources() {
        return catalog.listResources();
    }

    @GetMapping("/resources/{id}")
    @PreAuthorize(Roles.ALL_STAFF)
    SpaceResourceDetail getResource(@PathVariable Long id) {
        return catalog.getResource(id);
    }

    @PostMapping("/resources")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.ADMIN_MANAGER)
    SpaceResourceDetail createResource(@Valid @RequestBody SpaceResourceRequest request) {
        return catalog.createResource(request);
    }

    @PutMapping("/resources/{id}")
    @PreAuthorize(Roles.ADMIN_MANAGER)
    SpaceResourceDetail updateResource(@PathVariable Long id, @Valid @RequestBody SpaceResourceRequest request) {
        return catalog.updateResource(id, request);
    }

    @DeleteMapping("/resources/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.ADMIN_MANAGER)
    void deleteResource(@PathVariable Long id) {
        catalog.deleteResource(id);
    }

    // ---------------- amenity ----------------

    @GetMapping("/amenities")
    @PreAuthorize(Roles.ALL_STAFF)
    List<AmenityDetail> listAmenities() {
        return catalog.listAmenities();
    }

    @GetMapping("/amenities/{id}")
    @PreAuthorize(Roles.ALL_STAFF)
    AmenityDetail getAmenity(@PathVariable Long id) {
        return catalog.getAmenity(id);
    }

    @PostMapping("/amenities")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.ADMIN_MANAGER)
    AmenityDetail createAmenity(@Valid @RequestBody AmenityRequest request) {
        return catalog.createAmenity(request);
    }

    @PutMapping("/amenities/{id}")
    @PreAuthorize(Roles.ADMIN_MANAGER)
    AmenityDetail updateAmenity(@PathVariable Long id, @Valid @RequestBody AmenityRequest request) {
        return catalog.updateAmenity(id, request);
    }

    @DeleteMapping("/amenities/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.ADMIN_MANAGER)
    void deleteAmenity(@PathVariable Long id) {
        catalog.deleteAmenity(id);
    }

    // ---------------- price_rule ----------------

    @GetMapping("/price-rules")
    @PreAuthorize(Roles.FRONT_DESK)
    List<PriceRuleDetail> listPriceRules() {
        return catalog.listPriceRules();
    }

    @GetMapping("/price-rules/{id}")
    @PreAuthorize(Roles.FRONT_DESK)
    PriceRuleDetail getPriceRule(@PathVariable Long id) {
        return catalog.getPriceRule(id);
    }

    @PostMapping("/price-rules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.ADMIN_MANAGER)
    PriceRuleDetail createPriceRule(@Valid @RequestBody PriceRuleRequest request) {
        return catalog.createPriceRule(request);
    }

    @PutMapping("/price-rules/{id}")
    @PreAuthorize(Roles.ADMIN_MANAGER)
    PriceRuleDetail updatePriceRule(@PathVariable Long id, @Valid @RequestBody PriceRuleRequest request) {
        return catalog.updatePriceRule(id, request);
    }

    @DeleteMapping("/price-rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.ADMIN_MANAGER)
    void deletePriceRule(@PathVariable Long id) {
        catalog.deletePriceRule(id);
    }
}
