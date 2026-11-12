package com.cowork.business.service;

import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.dataaccess.entity.Branch;
import com.cowork.dataaccess.entity.SpaceResource;
import com.cowork.dataaccess.entity.Zone;
import com.cowork.dataaccess.enums.BookingStatus;
import com.cowork.dataaccess.enums.BranchStatus;
import com.cowork.dataaccess.enums.ResourceStatus;
import com.cowork.dataaccess.enums.ResourceTypeCode;
import com.cowork.dataaccess.repository.AmenityRepository;
import com.cowork.dataaccess.repository.BookingRepository;
import com.cowork.dataaccess.repository.BookingRepository.Occupancy;
import com.cowork.dataaccess.repository.BranchRepository;
import com.cowork.dataaccess.repository.SpaceResourceRepository;
import com.cowork.dataaccess.valueobject.TimeRange;
import com.cowork.dto.AvailabilityDtos.AmenityResponse;
import com.cowork.dto.AvailabilityDtos.AvailableResource;
import com.cowork.dto.AvailabilityDtos.BranchResponse;
import com.cowork.dto.AvailabilityDtos.FloorPlanResponse;
import com.cowork.dto.AvailabilityDtos.ResourceSlot;
import com.cowork.dto.AvailabilityDtos.SlotStatus;
import com.cowork.dto.AvailabilityDtos.ZonePlan;
import com.cowork.dto.PriceQuote;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * UC02: tra cứu chỗ trống. Hai cách xem trên cùng một dữ liệu:
 * <ul>
 *   <li>{@link #search}: danh sách vị trí đặt được ngay, kèm giá dự kiến;</li>
 *   <li>{@link #floorPlan}: sơ đồ mặt bằng với trạng thái từng vị trí.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AvailabilityService {

    private final BranchRepository branches;
    private final AmenityRepository amenities;
    private final SpaceResourceRepository resources;
    private final BookingRepository bookings;
    private final PricingService pricing;
    private final BookingWindowPolicy windowPolicy;

    public AvailabilityService(BranchRepository branches, AmenityRepository amenities, SpaceResourceRepository resources,
                               BookingRepository bookings, PricingService pricing, BookingWindowPolicy windowPolicy) {
        this.branches = branches;
        this.amenities = amenities;
        this.resources = resources;
        this.bookings = bookings;
        this.pricing = pricing;
        this.windowPolicy = windowPolicy;
    }

    /** Chi nhánh đang hoạt động, để khách chọn trước khi tìm. */
    public List<BranchResponse> branches() {
        return branches.findByStatusOrderByCodeAsc(BranchStatus.ACTIVE).stream().map(BranchResponse::from).toList();
    }

    /** Danh mục tiện ích, dùng làm bộ lọc (FR-SRCH-03). */
    public List<AmenityResponse> amenities() {
        return amenities.findAllByOrderByCodeAsc().stream().map(AmenityResponse::from).toList();
    }

    /**
     * FR-SRCH-01, 03, 04, 06: vị trí của chi nhánh đặt được ngay trong khung giờ — đang khai thác (BR-09),
     * đủ sức chứa (BR-14), có đủ mọi tiện ích yêu cầu, không có đơn hiệu lực giao khung giờ (BR-01).
     */
    public List<AvailableResource> search(Long branchId, ResourceTypeCode type, LocalDateTime startAt, LocalDateTime endAt,
                                          int partySize, List<String> amenityCodes) {
        if (partySize < 1) {
            throw new DomainException("INVALID_PARTY_SIZE", "Số người phải từ 1 trở lên");
        }
        Branch branch = loadBranch(branchId);
        TimeRange range = windowPolicy.validate(startAt, endAt, branch);
        Set<String> required = requireKnownAmenities(amenityCodes);

        List<SpaceResource> candidates = resourcesOf(branchId, type).stream()
                .filter(r -> r.getStatus() == ResourceStatus.ACTIVE)
                .filter(r -> r.getCapacity() >= partySize)
                .filter(r -> amenityCodesOf(r).containsAll(required))
                .toList();
        Map<Long, SlotStatus> occupied = occupancy(candidates, range);
        List<SpaceResource> free = candidates.stream().filter(r -> !occupied.containsKey(r.getId())).toList();
        if (free.isEmpty()) {
            return List.of();
        }
        PriceQuote quote = pricing.quote(type, range);
        return free.stream().map(r -> AvailableResource.from(r, quote)).toList();
    }

    /**
     * FR-SRCH-02: mọi vị trí của chi nhánh (trừ đã ngưng khai thác) theo từng khu vực, kèm tọa độ và trạng thái
     * trong khung giờ. {@code type} rỗng = mọi loại không gian.
     */
    public FloorPlanResponse floorPlan(Long branchId, ResourceTypeCode type, LocalDateTime startAt, LocalDateTime endAt) {
        Branch branch = loadBranch(branchId);
        TimeRange range = windowPolicy.validate(startAt, endAt, branch);

        List<SpaceResource> onPlan = resourcesOf(branchId, type);
        Map<Long, SlotStatus> occupied = occupancy(onPlan, range);

        // Danh sách đã sắp theo tầng → khu vực → mã, nên LinkedHashMap giữ đúng thứ tự đó khi gom theo khu vực.
        Map<Zone, List<ResourceSlot>> slotsByZone = new LinkedHashMap<>();
        for (SpaceResource r : onPlan) {
            slotsByZone.computeIfAbsent(r.getZone(), z -> new ArrayList<>()).add(ResourceSlot.from(r, statusOf(r, occupied)));
        }
        List<ZonePlan> zones = slotsByZone.entrySet().stream()
                .map(e -> new ZonePlan(e.getKey().getId(), e.getKey().getName(), e.getKey().getFloor(),
                        e.getKey().getFloorPlanUrl(), e.getValue()))
                .toList();
        return new FloorPlanResponse(branch.getId(), branch.getName(), branch.getOpenTime(), branch.getCloseTime(),
                range.startAt(), range.endAt(), zones);
    }

    // Lọc loại trong Java thay vì "(:type is null or ...)" trong JPQL: tham số null kiểu đó dễ lỗi trên PostgreSQL,
    // và mỗi chi nhánh chỉ có vài chục vị trí.
    private List<SpaceResource> resourcesOf(Long branchId, ResourceTypeCode type) {
        return resources.findInBranch(branchId).stream()
                .filter(r -> type == null || r.getType() == type)
                .toList();
    }

    private Branch loadBranch(Long id) {
        return branches.findById(id).orElseThrow(() -> new NotFoundException("Chi nhánh", id));
    }

    /** Chuẩn hóa mã tiện ích (bỏ khoảng trắng, viết hoa) và báo lỗi nếu có mã không tồn tại, thay vì âm thầm trả rỗng. */
    private Set<String> requireKnownAmenities(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Set.of();
        }
        Set<String> requested = codes.stream()
                .map(c -> c.trim().toUpperCase(Locale.ROOT))
                .filter(c -> !c.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
        if (requested.isEmpty()) {
            return Set.of();
        }
        Set<String> missing = new TreeSet<>(requested);
        amenities.findByCodeIn(requested).forEach(a -> missing.remove(a.getCode()));
        if (!missing.isEmpty()) {
            throw new DomainException("UNKNOWN_AMENITY", "Tiện ích không tồn tại: " + String.join(", ", missing));
        }
        return requested;
    }

    private static Set<String> amenityCodesOf(SpaceResource r) {
        return r.getAmenities().stream().map(ra -> ra.getAmenity().getCode()).collect(Collectors.toSet());
    }

    /** Vị trí nào đang bị đơn chiếm trong khung giờ, và bị chiếm kiểu gì. Một vị trí vừa có đơn giữ vừa có đơn đã đặt → BOOKED. */
    private Map<Long, SlotStatus> occupancy(List<SpaceResource> rs, TimeRange range) {
        Map<Long, SlotStatus> result = new HashMap<>();
        List<Long> ids = rs.stream().map(SpaceResource::getId).toList();
        for (Occupancy o : bookings.findOccupancies(ids, range)) {
            SlotStatus status = o.getStatus() == BookingStatus.HELD ? SlotStatus.HELD : SlotStatus.BOOKED;
            result.merge(o.getResourceId(), status, (a, b) -> a == SlotStatus.BOOKED ? a : b);
        }
        // Chỗ mới của yêu cầu đổi chỗ đang chờ thanh toán: giống giữ chỗ tạm.
        for (Long resourceId : bookings.findPendingResourceIds(ids, range)) {
            result.putIfAbsent(resourceId, SlotStatus.HELD);
        }
        return result;
    }

    private static SlotStatus statusOf(SpaceResource r, Map<Long, SlotStatus> occupied) {
        if (r.getStatus() == ResourceStatus.MAINTENANCE) {
            return SlotStatus.MAINTENANCE;
        }
        return occupied.getOrDefault(r.getId(), SlotStatus.AVAILABLE);
    }
}
