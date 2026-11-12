package com.cowork.business.service;

import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.dataaccess.entity.Amenity;
import com.cowork.dataaccess.entity.Branch;
import com.cowork.dataaccess.entity.PriceRule;
import com.cowork.dataaccess.entity.ResourceAmenity;
import com.cowork.dataaccess.entity.ResourceType;
import com.cowork.dataaccess.entity.SpaceResource;
import com.cowork.dataaccess.entity.Zone;
import com.cowork.dataaccess.enums.BranchStatus;
import com.cowork.dataaccess.enums.ResourceTypeCode;
import com.cowork.dataaccess.repository.AmenityRepository;
import com.cowork.dataaccess.repository.BranchRepository;
import com.cowork.dataaccess.repository.PriceRuleRepository;
import com.cowork.dataaccess.repository.ResourceTypeRepository;
import com.cowork.dataaccess.repository.SpaceResourceRepository;
import com.cowork.dataaccess.repository.ZoneRepository;
import com.cowork.dto.CatalogDtos.AmenityDetail;
import com.cowork.dto.CatalogDtos.AmenityQuantity;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * UC19, UC20: quản trị danh mục không gian và bảng giá — chi nhánh, khu vực, loại chỗ, vị trí, tiện ích, giá theo block.
 * Trả về DTO (chuyển ngay trong transaction) vì nhiều bảng có quan hệ LAZY, ví dụ tiện ích của vị trí.
 * Xóa dòng đang được dữ liệu khác tham chiếu → database từ chối → 409 {@code DATA_CONFLICT}.
 */
@Service
@Transactional
public class CatalogService {

    private final BranchRepository branches;
    private final ZoneRepository zones;
    private final ResourceTypeRepository resourceTypes;
    private final SpaceResourceRepository resources;
    private final AmenityRepository amenities;
    private final PriceRuleRepository priceRules;
    private final Clock clock;

    public CatalogService(BranchRepository branches, ZoneRepository zones, ResourceTypeRepository resourceTypes,
                          SpaceResourceRepository resources, AmenityRepository amenities, PriceRuleRepository priceRules,
                          Clock clock) {
        this.branches = branches;
        this.zones = zones;
        this.resourceTypes = resourceTypes;
        this.resources = resources;
        this.amenities = amenities;
        this.priceRules = priceRules;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // branch
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<BranchDetail> listBranches() {
        return branches.findAllByOrderByCodeAsc().stream().map(BranchDetail::from).toList();
    }

    @Transactional(readOnly = true)
    public BranchDetail getBranch(Long id) {
        return BranchDetail.from(branch(id));
    }

    public BranchDetail createBranch(BranchRequest request) {
        String code = request.code().trim();
        if (branches.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("CODE_ALREADY_EXISTS", "Mã chi nhánh đã tồn tại: " + code);
        }
        Branch branch = new Branch();
        branch.setCreatedAt(LocalDateTime.now(clock));
        branch.setStatus(BranchStatus.ACTIVE);
        applyBranch(branch, request, code);
        return BranchDetail.from(branches.save(branch));
    }

    public BranchDetail updateBranch(Long id, BranchRequest request) {
        Branch branch = branch(id);
        String code = request.code().trim();
        if (!code.equalsIgnoreCase(branch.getCode()) && branches.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("CODE_ALREADY_EXISTS", "Mã chi nhánh đã tồn tại: " + code);
        }
        applyBranch(branch, request, code);
        return BranchDetail.from(branches.save(branch));
    }

    public void deleteBranch(Long id) {
        branches.delete(branch(id));
        branches.flush();
    }

    private static void applyBranch(Branch branch, BranchRequest request, String code) {
        if (!request.openTime().isBefore(request.closeTime())) {
            throw new DomainException("INVALID_TIME_RANGE", "Giờ đóng cửa phải sau giờ mở cửa");
        }
        branch.setCode(code);
        branch.setName(request.name().trim());
        branch.setAddress(request.address().trim());
        branch.setOpenTime(request.openTime());
        branch.setCloseTime(request.closeTime());
        if (request.status() != null) {
            branch.setStatus(request.status());
        }
    }

    // ------------------------------------------------------------------
    // zone
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ZoneDetail> listZones() {
        return zones.findAllByOrderByIdAsc().stream().map(ZoneDetail::from).toList();
    }

    @Transactional(readOnly = true)
    public ZoneDetail getZone(Long id) {
        return ZoneDetail.from(zone(id));
    }

    public ZoneDetail createZone(ZoneRequest request) {
        Zone zone = new Zone();
        applyZone(zone, request);
        return ZoneDetail.from(zones.save(zone));
    }

    public ZoneDetail updateZone(Long id, ZoneRequest request) {
        Zone zone = zone(id);
        applyZone(zone, request);
        return ZoneDetail.from(zones.save(zone));
    }

    public void deleteZone(Long id) {
        zones.delete(zone(id));
        zones.flush();
    }

    private void applyZone(Zone zone, ZoneRequest request) {
        zone.setBranch(branch(request.branchId()));
        zone.setName(request.name().trim());
        zone.setFloor(request.floor());
        zone.setFloorPlanUrl(request.floorPlanUrl());
    }

    // ------------------------------------------------------------------
    // resource_type
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ResourceTypeDetail> listResourceTypes() {
        return resourceTypes.findAllByOrderByIdAsc().stream().map(ResourceTypeDetail::from).toList();
    }

    @Transactional(readOnly = true)
    public ResourceTypeDetail getResourceType(Long id) {
        return ResourceTypeDetail.from(resourceType(id));
    }

    /** Mã loại phải là một giá trị của enum {@link ResourceTypeCode} (code tính giá dựa vào enum này). */
    public ResourceTypeDetail createResourceType(ResourceTypeRequest request) {
        if (resourceTypes.findByCode(request.code()).isPresent()) {
            throw new ConflictException("CODE_ALREADY_EXISTS", "Loại không gian đã tồn tại: " + request.code());
        }
        ResourceType type = new ResourceType(request.code(), request.name().trim(), request.description());
        return ResourceTypeDetail.from(resourceTypes.save(type));
    }

    public ResourceTypeDetail updateResourceType(Long id, ResourceTypeRequest request) {
        ResourceType type = resourceType(id);
        if (request.code() != type.getCode()) {
            throw new DomainException("CODE_IMMUTABLE", "Không đổi được mã loại không gian, chỉ sửa tên và mô tả");
        }
        type.setName(request.name().trim());
        type.setDescription(request.description());
        return ResourceTypeDetail.from(resourceTypes.save(type));
    }

    public void deleteResourceType(Long id) {
        resourceTypes.delete(resourceType(id));
        resourceTypes.flush();
    }

    // ------------------------------------------------------------------
    // space_resource
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SpaceResourceDetail> listResources() {
        return resources.findAllByOrderByCodeAsc().stream().map(SpaceResourceDetail::from).toList();
    }

    @Transactional(readOnly = true)
    public SpaceResourceDetail getResource(Long id) {
        return SpaceResourceDetail.from(resource(id));
    }

    public SpaceResourceDetail createResource(SpaceResourceRequest request) {
        String code = request.code().trim();
        if (resources.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("CODE_ALREADY_EXISTS", "Mã vị trí đã tồn tại: " + code);
        }
        SpaceResource resource = new SpaceResource(code, request.name().trim(), resourceTypeByCode(request.type()), request.capacity());
        applyResource(resource, request);
        return SpaceResourceDetail.from(resources.save(resource));
    }

    public SpaceResourceDetail updateResource(Long id, SpaceResourceRequest request) {
        SpaceResource resource = resource(id);
        String code = request.code().trim();
        if (!code.equalsIgnoreCase(resource.getCode()) && resources.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("CODE_ALREADY_EXISTS", "Mã vị trí đã tồn tại: " + code);
        }
        resource.setCode(code);
        resource.setName(request.name().trim());
        resource.setResourceType(resourceTypeByCode(request.type()));
        resource.setCapacity(request.capacity());
        applyResource(resource, request);
        return SpaceResourceDetail.from(resources.save(resource));
    }

    /** Vị trí đã có đơn thì không xóa được (409 DATA_CONFLICT) — chuyển sang INACTIVE để ngưng khai thác. */
    public void deleteResource(Long id) {
        resources.delete(resource(id));
        resources.flush();
    }

    private void applyResource(SpaceResource resource, SpaceResourceRequest request) {
        if (request.status() != null) {
            resource.setStatus(request.status());
        }
        resource.setZone(request.zoneId() == null ? null : zone(request.zoneId()));
        resource.setPosX(request.posX());
        resource.setPosY(request.posY());
        if (request.amenities() != null) {
            syncAmenities(resource, request.amenities());
        }
    }

    /**
     * Đồng bộ danh sách tiện ích theo kiểu so khớp: sửa số lượng dòng đã có, thêm dòng mới, bỏ dòng không còn.
     * Không xóa hết rồi thêm lại, vì Hibernate chạy INSERT trước DELETE → trùng {@code uq_resource_amenity}.
     */
    private void syncAmenities(SpaceResource resource, List<AmenityQuantity> wanted) {
        Map<String, Integer> quantities = new HashMap<>();
        for (AmenityQuantity a : wanted) {
            String code = a.code().trim().toUpperCase(Locale.ROOT);
            if (quantities.put(code, a.quantity()) != null) {
                throw new DomainException("DUPLICATE_AMENITY", "Tiện ích bị lặp: " + code);
            }
        }
        Map<String, Amenity> known = new HashMap<>();
        amenities.findByCodeIn(quantities.keySet()).forEach(a -> known.put(a.getCode(), a));
        for (String code : quantities.keySet()) {
            if (!known.containsKey(code)) {
                throw new DomainException("UNKNOWN_AMENITY", "Tiện ích không tồn tại: " + code);
            }
        }
        resource.getAmenities().removeIf(ra -> !quantities.containsKey(ra.getAmenity().getCode()));
        for (ResourceAmenity ra : resource.getAmenities()) {
            ra.setQuantity(quantities.remove(ra.getAmenity().getCode()));
        }
        quantities.forEach((code, quantity) -> resource.addAmenity(new ResourceAmenity(known.get(code), quantity)));
    }

    // ------------------------------------------------------------------
    // amenity
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AmenityDetail> listAmenities() {
        return amenities.findAllByOrderByCodeAsc().stream().map(AmenityDetail::from).toList();
    }

    @Transactional(readOnly = true)
    public AmenityDetail getAmenity(Long id) {
        return AmenityDetail.from(amenity(id));
    }

    /** Mã tiện ích lưu chữ hoa, vì tìm chỗ trống (UC02) so khớp mã đã viết hoa. */
    public AmenityDetail createAmenity(AmenityRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (amenities.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("CODE_ALREADY_EXISTS", "Mã tiện ích đã tồn tại: " + code);
        }
        return AmenityDetail.from(amenities.save(new Amenity(code, request.name().trim())));
    }

    public AmenityDetail updateAmenity(Long id, AmenityRequest request) {
        Amenity amenity = amenity(id);
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (!code.equals(amenity.getCode()) && amenities.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("CODE_ALREADY_EXISTS", "Mã tiện ích đã tồn tại: " + code);
        }
        amenity.setCode(code);
        amenity.setName(request.name().trim());
        return AmenityDetail.from(amenities.save(amenity));
    }

    public void deleteAmenity(Long id) {
        amenities.delete(amenity(id));
        amenities.flush();
    }

    // ------------------------------------------------------------------
    // price_rule (BR-03, BR-04)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PriceRuleDetail> listPriceRules() {
        return priceRules.findAllByOrderByIdAsc().stream().map(PriceRuleDetail::from).toList();
    }

    @Transactional(readOnly = true)
    public PriceRuleDetail getPriceRule(Long id) {
        return PriceRuleDetail.from(priceRule(id));
    }

    public PriceRuleDetail createPriceRule(PriceRuleRequest request) {
        requireNoOverlap(request, null);
        PriceRule rule = new PriceRule(resourceTypeByCode(request.type()), request.dayType(), request.startMinute(),
                request.endMinute(), request.pricePerBlock(), request.label().trim());
        return PriceRuleDetail.from(priceRules.save(rule));
    }

    public PriceRuleDetail updatePriceRule(Long id, PriceRuleRequest request) {
        PriceRule rule = priceRule(id);
        requireNoOverlap(request, id);
        rule.setResourceType(resourceTypeByCode(request.type()));
        rule.setDayType(request.dayType());
        rule.setStartMinute(request.startMinute());
        rule.setEndMinute(request.endMinute());
        rule.setPricePerBlock(request.pricePerBlock());
        rule.setLabel(request.label().trim());
        return PriceRuleDetail.from(priceRules.save(rule));
    }

    public void deletePriceRule(Long id) {
        priceRules.delete(priceRule(id));
        priceRules.flush();
    }

    /** Cùng loại chỗ, cùng loại ngày thì các khung giá không được chồng nhau (giống ex_price_rule_no_overlap). */
    private void requireNoOverlap(PriceRuleRequest request, Long excludeId) {
        if (request.startMinute() >= request.endMinute()) {
            throw new DomainException("INVALID_TIME_RANGE", "Phút kết thúc phải lớn hơn phút bắt đầu");
        }
        boolean overlap = priceRules.findByResourceTypeCode(request.type()).stream()
                .filter(r -> !r.getId().equals(excludeId))
                .filter(r -> r.getDayType() == request.dayType())
                .anyMatch(r -> r.getStartMinute() < request.endMinute() && request.startMinute() < r.getEndMinute());
        if (overlap) {
            throw new ConflictException("PRICE_RULE_OVERLAP", "Khung giờ giá bị chồng với một mức giá đã có");
        }
    }

    // ------------------------------------------------------------------
    // Hàm phụ
    // ------------------------------------------------------------------

    private Branch branch(Long id) {
        return branches.findById(id).orElseThrow(() -> new NotFoundException("Chi nhánh", id));
    }

    private Zone zone(Long id) {
        return zones.findById(id).orElseThrow(() -> new NotFoundException("Khu vực", id));
    }

    private ResourceType resourceType(Long id) {
        return resourceTypes.findById(id).orElseThrow(() -> new NotFoundException("Loại không gian", id));
    }

    private ResourceType resourceTypeByCode(ResourceTypeCode code) {
        return resourceTypes.findByCode(code).orElseThrow(() -> new NotFoundException("Loại không gian", code));
    }

    private SpaceResource resource(Long id) {
        return resources.findById(id).orElseThrow(() -> new NotFoundException("Vị trí", id));
    }

    private Amenity amenity(Long id) {
        return amenities.findById(id).orElseThrow(() -> new NotFoundException("Tiện ích", id));
    }

    private PriceRule priceRule(Long id) {
        return priceRules.findById(id).orElseThrow(() -> new NotFoundException("Mức giá", id));
    }
}
