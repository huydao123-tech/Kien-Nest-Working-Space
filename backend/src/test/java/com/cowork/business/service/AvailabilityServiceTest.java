package com.cowork.business.service;

import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.dataaccess.entity.Amenity;
import com.cowork.dataaccess.entity.Branch;
import com.cowork.dataaccess.entity.ResourceAmenity;
import com.cowork.dataaccess.entity.ResourceType;
import com.cowork.dataaccess.entity.SpaceResource;
import com.cowork.dataaccess.entity.Zone;
import com.cowork.dataaccess.enums.BookingStatus;
import com.cowork.dataaccess.enums.ResourceStatus;
import com.cowork.dataaccess.enums.ResourceTypeCode;
import com.cowork.dataaccess.repository.AmenityRepository;
import com.cowork.dataaccess.repository.BookingRepository;
import com.cowork.dataaccess.repository.BookingRepository.Occupancy;
import com.cowork.dataaccess.repository.BranchRepository;
import com.cowork.dataaccess.repository.SpaceResourceRepository;
import com.cowork.dataaccess.valueobject.Money;
import com.cowork.dataaccess.valueobject.TimeRange;
import com.cowork.dto.AvailabilityDtos.AvailableResource;
import com.cowork.dto.AvailabilityDtos.FloorPlanResponse;
import com.cowork.dto.AvailabilityDtos.ResourceSlot;
import com.cowork.dto.AvailabilityDtos.SlotStatus;
import com.cowork.dto.PriceQuote;
import com.cowork.support.TestCaseLogRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC02, FR-SRCH-01..06: tìm chỗ trống và sơ đồ mặt bằng. Viết theo {@code .claude/rules/unit-test.md}.
 * Chi nhánh mẫu có HD-01, HD-02 (HOT_DESK, 1 người, có ổ cắm) và MR-01 (MEETING_ROOM, 6 người).
 */
public class AvailabilityServiceTest {

    private static final Long BRANCH_ID = 1L;
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 3, 9, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 3, 11, 0);
    private static final TimeRange RANGE = new TimeRange(START, END);
    private static final PriceQuote QUOTE = new PriceQuote(4, 30, Money.vnd(100_000));

    @Rule
    public final TestCaseLogRule log = new TestCaseLogRule();

    private AmenityRepository amenities;
    private BookingRepository bookings;
    private PricingService pricing;
    private AvailabilityService service;
    private Branch branch;
    private Amenity outlet;
    private SpaceResource hd1;
    private SpaceResource hd2;
    private SpaceResource mr1;

    @Before
    public void setUp() {
        BranchRepository branches = mock(BranchRepository.class);
        amenities = mock(AmenityRepository.class);
        SpaceResourceRepository resources = mock(SpaceResourceRepository.class);
        bookings = mock(BookingRepository.class);
        pricing = mock(PricingService.class);
        BookingWindowPolicy windowPolicy = mock(BookingWindowPolicy.class);
        service = new AvailabilityService(branches, amenities, resources, bookings, pricing, windowPolicy);

        branch = new Branch("HCM-Q1", "Cowork Nguyễn Huệ", "72 Nguyễn Huệ", LocalTime.of(7, 0), LocalTime.of(22, 0));
        branch.setId(BRANCH_ID);
        when(branches.findById(BRANCH_ID)).thenReturn(Optional.of(branch));
        when(windowPolicy.validate(START, END, branch)).thenReturn(RANGE);

        Zone open = zone(10L, "Khu mở", 1);
        Zone meeting = zone(11L, "Khu họp", 3);
        outlet = new Amenity("POWER_OUTLET", "Ổ cắm");
        hd1 = resource(1L, "HD-01", ResourceTypeCode.HOT_DESK, 1, open);
        hd1.addAmenity(new ResourceAmenity(outlet, 2));
        hd2 = resource(2L, "HD-02", ResourceTypeCode.HOT_DESK, 1, open);
        hd2.addAmenity(new ResourceAmenity(outlet, 2));
        mr1 = resource(3L, "MR-01", ResourceTypeCode.MEETING_ROOM, 6, meeting);
        when(resources.findInBranch(BRANCH_ID)).thenReturn(List.of(hd1, hd2, mr1));
        when(bookings.findOccupancies(anyCollection(), any())).thenReturn(List.of());
        when(bookings.findPendingResourceIds(anyCollection(), any())).thenReturn(List.of());
        when(pricing.quote(any(), any())).thenReturn(QUOTE);
    }

    // ---------- search ----------

    // Viewpoint: VP3, VP4
    @Test
    public void test_search_AllFree_001() {
        // Không có đơn nào: trả mọi hot desk, kèm giá.
        log.input("branchId = {}, type = {}, startAt = {}, endAt = {}, partySize = {}, amenityCodes = {}",
                BRANCH_ID, ResourceTypeCode.HOT_DESK, START, END, 1, null);

        List<AvailableResource> result = service.search(BRANCH_ID, ResourceTypeCode.HOT_DESK, START, END, 1, null);

        log.output("codes = {}, quote = {}", codes(result), result.get(0).quote());
        assertEquals(List.of("HD-01", "HD-02"), codes(result));
        assertEquals(QUOTE, result.get(0).quote());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_search_PartySizeZero_002() {
        // Cận dưới: 0 người.
        log.input("branchId = {}, type = {}, startAt = {}, endAt = {}, partySize = {}, amenityCodes = {}",
                BRANCH_ID, ResourceTypeCode.HOT_DESK, START, END, 0, null);

        DomainException thrown = assertThrows(DomainException.class,
                () -> service.search(BRANCH_ID, ResourceTypeCode.HOT_DESK, START, END, 0, null));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_PARTY_SIZE", thrown.code());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_search_PartySizeOverCapacity_003() {
        // 2 người: hot desk sức chứa 1 bị loại (BR-14).
        log.input("branchId = {}, type = {}, startAt = {}, endAt = {}, partySize = {}, amenityCodes = {}",
                BRANCH_ID, ResourceTypeCode.HOT_DESK, START, END, 2, null);

        List<AvailableResource> result = service.search(BRANCH_ID, ResourceTypeCode.HOT_DESK, START, END, 2, null);

        log.output("size = {}", result.size());
        assertEquals(0, result.size());
        verify(pricing, never()).quote(any(), any());
    }

    // Viewpoint: VP7
    @Test
    public void test_search_BranchNotFound_004() {
        // Chi nhánh không có.
        log.input("branchId = {}, type = {}, startAt = {}, endAt = {}, partySize = {}, amenityCodes = {}",
                99L, ResourceTypeCode.HOT_DESK, START, END, 1, null);

        NotFoundException thrown = assertThrows(NotFoundException.class,
                () -> service.search(99L, ResourceTypeCode.HOT_DESK, START, END, 1, null));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_search_MaintenanceExcluded_005() {
        // Vị trí đang bảo trì không được trả (BR-09).
        hd1.setStatus(ResourceStatus.MAINTENANCE);
        log.input("branchId = {}, type = {}, startAt = {}, endAt = {}, partySize = {}, amenityCodes = {}",
                BRANCH_ID, ResourceTypeCode.HOT_DESK, START, END, 1, null);

        List<AvailableResource> result = service.search(BRANCH_ID, ResourceTypeCode.HOT_DESK, START, END, 1, null);

        log.output("codes = {}", codes(result));
        assertEquals(List.of("HD-02"), codes(result));
    }

    // Viewpoint: VP3, VP8
    @Test
    public void test_search_OccupiedAndPendingExcluded_006() {
        // HD-01 có đơn CONFIRMED, HD-02 đang được giữ cho một yêu cầu đổi chỗ chờ thanh toán: không còn chỗ.
        when(bookings.findOccupancies(anyCollection(), any())).thenReturn(List.of(occupancy(1L, BookingStatus.CONFIRMED)));
        when(bookings.findPendingResourceIds(anyCollection(), any())).thenReturn(List.of(2L));
        log.input("branchId = {}, type = {}, startAt = {}, endAt = {}, partySize = {}, amenityCodes = {}",
                BRANCH_ID, ResourceTypeCode.HOT_DESK, START, END, 1, null);

        List<AvailableResource> result = service.search(BRANCH_ID, ResourceTypeCode.HOT_DESK, START, END, 1, null);

        log.output("size = {}", result.size());
        assertEquals(0, result.size());
    }

    // Viewpoint: VP2, VP4
    @Test
    public void test_search_AmenityCodeNormalized_007() {
        // Mã tiện ích viết thường, có khoảng trắng: vẫn khớp; phòng họp không có ổ cắm bị loại.
        when(amenities.findByCodeIn(Set.of("POWER_OUTLET"))).thenReturn(List.of(outlet));
        List<String> codes = List.of("  power_outlet ");
        log.input("branchId = {}, type = {}, startAt = {}, endAt = {}, partySize = {}, amenityCodes = {}",
                BRANCH_ID, ResourceTypeCode.HOT_DESK, START, END, 1, codes);

        List<AvailableResource> result = service.search(BRANCH_ID, ResourceTypeCode.HOT_DESK, START, END, 1, codes);

        log.output("codes = {}", codes(result));
        assertEquals(List.of("HD-01", "HD-02"), codes(result));
    }

    // Viewpoint: VP3
    @Test
    public void test_search_ResourceMissingAmenity_008() {
        // Phòng họp không có ổ cắm → không trả về.
        when(amenities.findByCodeIn(Set.of("POWER_OUTLET"))).thenReturn(List.of(outlet));
        List<String> codes = List.of("POWER_OUTLET");
        log.input("branchId = {}, type = {}, startAt = {}, endAt = {}, partySize = {}, amenityCodes = {}",
                BRANCH_ID, ResourceTypeCode.MEETING_ROOM, START, END, 1, codes);

        List<AvailableResource> result = service.search(BRANCH_ID, ResourceTypeCode.MEETING_ROOM, START, END, 1, codes);

        log.output("size = {}", result.size());
        assertEquals(0, result.size());
    }

    // Viewpoint: VP3
    @Test
    public void test_search_UnknownAmenity_009() {
        // Mã tiện ích không tồn tại.
        when(amenities.findByCodeIn(any())).thenReturn(List.of());
        List<String> codes = List.of("JACUZZI");
        log.input("branchId = {}, type = {}, startAt = {}, endAt = {}, partySize = {}, amenityCodes = {}",
                BRANCH_ID, ResourceTypeCode.HOT_DESK, START, END, 1, codes);

        DomainException thrown = assertThrows(DomainException.class,
                () -> service.search(BRANCH_ID, ResourceTypeCode.HOT_DESK, START, END, 1, codes));

        log.output("code = {}", thrown.code());
        assertEquals("UNKNOWN_AMENITY", thrown.code());
    }

    // Viewpoint: VP2
    @Test
    public void test_search_BlankAmenityCodesIgnored_010() {
        // Danh sách chỉ có chuỗi rỗng: coi như không lọc tiện ích.
        List<String> codes = new ArrayList<>(List.of(" ", ""));
        log.input("branchId = {}, type = {}, startAt = {}, endAt = {}, partySize = {}, amenityCodes = {}",
                BRANCH_ID, ResourceTypeCode.MEETING_ROOM, START, END, 1, codes);

        List<AvailableResource> result = service.search(BRANCH_ID, ResourceTypeCode.MEETING_ROOM, START, END, 1, codes);

        log.output("codes = {}", codes(result));
        assertEquals(List.of("MR-01"), codes(result));
        verify(amenities, never()).findByCodeIn(any());
    }

    // ---------- floorPlan ----------

    // Viewpoint: VP3, VP4
    @Test
    public void test_floorPlan_StatusesAndZones_011() {
        // HD-01 vừa có đơn giữ vừa có đơn đã đặt → BOOKED; HD-02 chờ đổi chỗ → HELD; MR-01 bảo trì; chia 2 khu vực.
        mr1.setStatus(ResourceStatus.MAINTENANCE);
        when(bookings.findOccupancies(anyCollection(), any())).thenReturn(List.of(
                occupancy(1L, BookingStatus.HELD), occupancy(1L, BookingStatus.CONFIRMED)));
        when(bookings.findPendingResourceIds(anyCollection(), any())).thenReturn(List.of(2L));
        log.input("branchId = {}, type = {}, startAt = {}, endAt = {}", BRANCH_ID, null, START, END);

        FloorPlanResponse plan = service.floorPlan(BRANCH_ID, null, START, END);

        List<ResourceSlot> openZone = plan.zones().get(0).resources();
        log.output("zones = {}, hd1 = {}, hd2 = {}, mr1 = {}, branchName = {}", plan.zones().size(), openZone.get(0).status(),
                openZone.get(1).status(), plan.zones().get(1).resources().get(0).status(), plan.branchName());
        assertEquals(2, plan.zones().size());
        assertEquals(SlotStatus.BOOKED, openZone.get(0).status());
        assertEquals(SlotStatus.HELD, openZone.get(1).status());
        assertEquals(SlotStatus.MAINTENANCE, plan.zones().get(1).resources().get(0).status());
        assertEquals("Cowork Nguyễn Huệ", plan.branchName());
    }

    // Viewpoint: VP3
    @Test
    public void test_floorPlan_FilterByTypeAllAvailable_012() {
        // Chỉ loại MEETING_ROOM, không có đơn: 1 khu vực, AVAILABLE.
        log.input("branchId = {}, type = {}, startAt = {}, endAt = {}", BRANCH_ID, ResourceTypeCode.MEETING_ROOM, START, END);

        FloorPlanResponse plan = service.floorPlan(BRANCH_ID, ResourceTypeCode.MEETING_ROOM, START, END);

        log.output("zones = {}, status = {}", plan.zones().size(), plan.zones().get(0).resources().get(0).status());
        assertEquals(1, plan.zones().size());
        assertEquals(SlotStatus.AVAILABLE, plan.zones().get(0).resources().get(0).status());
    }

    // Viewpoint: VP7
    @Test
    public void test_floorPlan_BranchNotFound_013() {
        // Chi nhánh không có.
        log.input("branchId = {}, type = {}, startAt = {}, endAt = {}", 99L, null, START, END);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.floorPlan(99L, null, START, END));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    private static Zone zone(Long id, String name, int floor) {
        Zone z = new Zone(null, name, floor, null);
        z.setId(id);
        return z;
    }

    private static SpaceResource resource(Long id, String code, ResourceTypeCode type, int capacity, Zone zone) {
        SpaceResource r = new SpaceResource(code, code, new ResourceType(type, type.name(), null), capacity);
        r.setId(id);
        r.setZone(zone);
        return r;
    }

    private static List<String> codes(List<AvailableResource> result) {
        return result.stream().map(AvailableResource::code).toList();
    }

    private static Occupancy occupancy(Long resourceId, BookingStatus status) {
        return new Occupancy() {
            @Override
            public Long getResourceId() {
                return resourceId;
            }

            @Override
            public BookingStatus getStatus() {
                return status;
            }
        };
    }
}
