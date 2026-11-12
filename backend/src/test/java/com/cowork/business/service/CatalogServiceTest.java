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
import com.cowork.dataaccess.enums.DayType;
import com.cowork.dataaccess.enums.ResourceStatus;
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
import com.cowork.support.MutableClock;
import com.cowork.support.TestCaseLogRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC19, UC20: quản trị chi nhánh, khu vực, loại chỗ, vị trí (kèm tiện ích), tiện ích, bảng giá.
 * Viết theo {@code .claude/rules/unit-test.md}.
 */
public class CatalogServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 8, 0);
    private static final LocalTime OPEN = LocalTime.of(7, 0);
    private static final LocalTime CLOSE = LocalTime.of(22, 0);

    @Rule
    public final TestCaseLogRule log = new TestCaseLogRule();

    private BranchRepository branches;
    private ZoneRepository zones;
    private ResourceTypeRepository resourceTypes;
    private SpaceResourceRepository resources;
    private AmenityRepository amenities;
    private PriceRuleRepository priceRules;
    private CatalogService service;
    private Branch branch;
    private Zone zone;
    private ResourceType hotDesk;
    private Amenity outlet;
    private Amenity monitor;

    @Before
    public void setUp() {
        MutableClock clock = new MutableClock(ZoneId.of("Asia/Ho_Chi_Minh"));
        clock.set(NOW);
        branches = mock(BranchRepository.class);
        zones = mock(ZoneRepository.class);
        resourceTypes = mock(ResourceTypeRepository.class);
        resources = mock(SpaceResourceRepository.class);
        amenities = mock(AmenityRepository.class);
        priceRules = mock(PriceRuleRepository.class);
        service = new CatalogService(branches, zones, resourceTypes, resources, amenities, priceRules, clock);

        branch = new Branch("HCM-Q1", "Cowork Nguyễn Huệ", "72 Nguyễn Huệ", OPEN, CLOSE);
        branch.setId(1L);
        zone = new Zone(branch, "Khu mở", 1, null);
        zone.setId(10L);
        hotDesk = new ResourceType(ResourceTypeCode.HOT_DESK, "Hot desk", null);
        hotDesk.setId(20L);
        outlet = new Amenity("POWER_OUTLET", "Ổ cắm");
        monitor = new Amenity("MONITOR", "Màn hình");
        when(branches.findById(1L)).thenReturn(Optional.of(branch));
        when(zones.findById(10L)).thenReturn(Optional.of(zone));
        when(resourceTypes.findById(20L)).thenReturn(Optional.of(hotDesk));
        when(resourceTypes.findByCode(ResourceTypeCode.HOT_DESK)).thenReturn(Optional.of(hotDesk));
        when(branches.save(any(Branch.class))).thenAnswer(call -> call.getArgument(0));
        when(zones.save(any(Zone.class))).thenAnswer(call -> call.getArgument(0));
        when(resourceTypes.save(any(ResourceType.class))).thenAnswer(call -> call.getArgument(0));
        when(resources.save(any(SpaceResource.class))).thenAnswer(call -> call.getArgument(0));
        when(amenities.save(any(Amenity.class))).thenAnswer(call -> call.getArgument(0));
        when(priceRules.save(any(PriceRule.class))).thenAnswer(call -> call.getArgument(0));
        when(priceRules.findByResourceTypeCode(any())).thenReturn(List.of());
    }

    // ---------- branch ----------

    // Viewpoint: VP7
    @Test
    public void test_getBranch_NotFound_001() {
        // Không có chi nhánh.
        log.input("id = {}", 99L);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.getBranch(99L));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP2, VP3
    @Test
    public void test_createBranch_Valid_002() {
        // Chi nhánh mới: cắt khoảng trắng, ACTIVE, ghi thời điểm tạo.
        BranchRequest request = new BranchRequest(" HN-01 ", " Cowork Hà Nội ", " 1 Tràng Tiền ", OPEN, CLOSE, null);
        log.input("request = {}", request);

        BranchDetail detail = service.createBranch(request);

        log.output("code = {}, name = {}, status = {}", detail.code(), detail.name(), detail.status());
        assertEquals("HN-01", detail.code());
        assertEquals("Cowork Hà Nội", detail.name());
        assertEquals(BranchStatus.ACTIVE, detail.status());
    }

    // Viewpoint: VP3
    @Test
    public void test_createBranch_CodeExists_003() {
        // Mã đã có.
        when(branches.existsByCodeIgnoreCase("HCM-Q1")).thenReturn(true);
        BranchRequest request = new BranchRequest("HCM-Q1", "Trùng", "Địa chỉ", OPEN, CLOSE, null);
        log.input("request = {}", request);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.createBranch(request));

        log.output("code = {}", thrown.code());
        assertEquals("CODE_ALREADY_EXISTS", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_createBranch_OpenEqualsClose_004() {
        // Giờ mở bằng giờ đóng.
        BranchRequest request = new BranchRequest("HN-02", "Sai giờ", "Địa chỉ", OPEN, OPEN, null);
        log.input("request = {}", request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.createBranch(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_TIME_RANGE", thrown.code());
    }

    // Viewpoint: VP3, VP5
    @Test
    public void test_updateBranch_SameCodeOtherCaseAndClose_005() {
        // Giữ mã (khác hoa thường) không coi là trùng; tạm ngưng chi nhánh.
        when(branches.existsByCodeIgnoreCase("hcm-q1")).thenReturn(true);
        BranchRequest request = new BranchRequest("hcm-q1", "Cowork", "72 Nguyễn Huệ", OPEN, CLOSE, BranchStatus.CLOSED);
        log.input("id = {}, request = {}", 1L, request);

        BranchDetail detail = service.updateBranch(1L, request);

        log.output("code = {}, status = {}", detail.code(), detail.status());
        assertEquals("hcm-q1", detail.code());
        assertEquals(BranchStatus.CLOSED, detail.status());
    }

    // Viewpoint: VP3
    @Test
    public void test_updateBranch_CodeTaken_006() {
        // Đổi sang mã của chi nhánh khác.
        when(branches.existsByCodeIgnoreCase("HN-01")).thenReturn(true);
        BranchRequest request = new BranchRequest("HN-01", "Cowork", "Địa chỉ", OPEN, CLOSE, null);
        log.input("id = {}, request = {}", 1L, request);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.updateBranch(1L, request));

        log.output("code = {}", thrown.code());
        assertEquals("CODE_ALREADY_EXISTS", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_deleteBranch_Found_007() {
        // Xóa và flush ngay.
        log.input("id = {}", 1L);

        service.deleteBranch(1L);

        log.output("deleted = {}", branch.getCode());
        assertEquals("HCM-Q1", branch.getCode());
        verify(branches).delete(branch);
        verify(branches).flush();
    }

    // ---------- zone ----------

    // Viewpoint: VP7
    @Test
    public void test_createZone_Valid_008() {
        // Khu vực mới thuộc chi nhánh.
        ZoneRequest request = new ZoneRequest(1L, " Tầng 2 ", 2, "/plan.png");
        log.input("request = {}", request);

        ZoneDetail detail = service.createZone(request);

        log.output("branchId = {}, name = {}, floor = {}", detail.branchId(), detail.name(), detail.floor());
        assertEquals(Long.valueOf(1L), detail.branchId());
        assertEquals("Tầng 2", detail.name());
        assertEquals(2, detail.floor());
    }

    // Viewpoint: VP7
    @Test
    public void test_createZone_BranchNotFound_009() {
        // Chi nhánh không có.
        ZoneRequest request = new ZoneRequest(99L, "Tầng 2", 2, null);
        log.input("request = {}", request);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.createZone(request));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
        verify(zones, never()).save(any());
    }

    // Viewpoint: VP5
    @Test
    public void test_updateZone_Valid_010() {
        // Sửa tên, tầng.
        ZoneRequest request = new ZoneRequest(1L, "Khu yên tĩnh", 2, null);
        log.input("id = {}, request = {}", 10L, request);

        ZoneDetail detail = service.updateZone(10L, request);

        log.output("name = {}, floor = {}", detail.name(), detail.floor());
        assertEquals("Khu yên tĩnh", detail.name());
        assertEquals(2, detail.floor());
    }

    // Viewpoint: VP7
    @Test
    public void test_deleteZone_NotFound_011() {
        // Không có khu vực.
        log.input("id = {}", 99L);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.deleteZone(99L));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // ---------- resource_type ----------

    // Viewpoint: VP3
    @Test
    public void test_createResourceType_New_012() {
        // Loại mới chưa có.
        ResourceTypeRequest request = new ResourceTypeRequest(ResourceTypeCode.MEETING_ROOM, " Phòng họp ", "Có TV");
        log.input("request = {}", request);

        ResourceTypeDetail detail = service.createResourceType(request);

        log.output("code = {}, name = {}", detail.code(), detail.name());
        assertEquals(ResourceTypeCode.MEETING_ROOM, detail.code());
        assertEquals("Phòng họp", detail.name());
    }

    // Viewpoint: VP3
    @Test
    public void test_createResourceType_Exists_013() {
        // Loại đã có.
        ResourceTypeRequest request = new ResourceTypeRequest(ResourceTypeCode.HOT_DESK, "Hot desk", null);
        log.input("request = {}", request);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.createResourceType(request));

        log.output("code = {}", thrown.code());
        assertEquals("CODE_ALREADY_EXISTS", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_updateResourceType_ChangeCode_014() {
        // Đổi mã loại: không cho.
        ResourceTypeRequest request = new ResourceTypeRequest(ResourceTypeCode.FIXED_DESK, "Bàn", null);
        log.input("id = {}, request = {}", 20L, request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.updateResourceType(20L, request));

        log.output("code = {}", thrown.code());
        assertEquals("CODE_IMMUTABLE", thrown.code());
    }

    // Viewpoint: VP5, VP7
    @Test
    public void test_updateResourceType_NameAndDescription_015() {
        // Sửa tên, xóa mô tả.
        ResourceTypeRequest request = new ResourceTypeRequest(ResourceTypeCode.HOT_DESK, " Bàn linh hoạt ", null);
        log.input("id = {}, request = {}", 20L, request);

        ResourceTypeDetail detail = service.updateResourceType(20L, request);

        log.output("name = {}, description = {}", detail.name(), detail.description());
        assertEquals("Bàn linh hoạt", detail.name());
        assertNull(detail.description());
    }

    // Viewpoint: VP7
    @Test
    public void test_deleteResourceType_Found_016() {
        // Xóa và flush.
        log.input("id = {}", 20L);

        service.deleteResourceType(20L);

        log.output("deleted = {}", hotDesk.getCode());
        assertEquals(ResourceTypeCode.HOT_DESK, hotDesk.getCode());
        verify(resourceTypes).delete(hotDesk);
        verify(resourceTypes).flush();
    }

    // ---------- space_resource ----------

    // Viewpoint: VP2, VP4
    @Test
    public void test_createResource_WithAmenities_017() {
        // Vị trí mới, mã tiện ích viết thường được viết hoa; 2 tiện ích.
        when(amenities.findByCodeIn(any())).thenReturn(List.of(outlet, monitor));
        SpaceResourceRequest request = new SpaceResourceRequest(" HD-09 ", "Hot desk 9", ResourceTypeCode.HOT_DESK, 1, null, 10L,
                20, 30, List.of(new AmenityQuantity(" power_outlet ", 2), new AmenityQuantity("MONITOR", 1)));
        log.input("request = {}", request);

        SpaceResourceDetail detail = service.createResource(request);

        log.output("code = {}, status = {}, zoneId = {}, amenities = {}", detail.code(), detail.status(), detail.zoneId(),
                detail.amenities().size());
        assertEquals("HD-09", detail.code());
        assertEquals(ResourceStatus.ACTIVE, detail.status());
        assertEquals(Long.valueOf(10L), detail.zoneId());
        assertEquals(2, detail.amenities().size());
    }

    // Viewpoint: VP3
    @Test
    public void test_createResource_CodeExists_018() {
        // Mã vị trí đã có.
        when(resources.existsByCodeIgnoreCase("HD-01")).thenReturn(true);
        SpaceResourceRequest request = new SpaceResourceRequest("HD-01", "Trùng", ResourceTypeCode.HOT_DESK, 1, null, null,
                null, null, null);
        log.input("request = {}", request);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.createResource(request));

        log.output("code = {}", thrown.code());
        assertEquals("CODE_ALREADY_EXISTS", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_createResource_DuplicateAmenity_019() {
        // Cùng tiện ích nhập 2 lần (khác hoa thường).
        SpaceResourceRequest request = new SpaceResourceRequest("HD-09", "Hot desk 9", ResourceTypeCode.HOT_DESK, 1, null, null,
                null, null, List.of(new AmenityQuantity("MONITOR", 1), new AmenityQuantity("monitor", 2)));
        log.input("request = {}", request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.createResource(request));

        log.output("code = {}", thrown.code());
        assertEquals("DUPLICATE_AMENITY", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_createResource_UnknownAmenity_020() {
        // Tiện ích không tồn tại.
        when(amenities.findByCodeIn(any())).thenReturn(List.of());
        SpaceResourceRequest request = new SpaceResourceRequest("HD-09", "Hot desk 9", ResourceTypeCode.HOT_DESK, 1, null, null,
                null, null, List.of(new AmenityQuantity("JACUZZI", 1)));
        log.input("request = {}", request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.createResource(request));

        log.output("code = {}", thrown.code());
        assertEquals("UNKNOWN_AMENITY", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_createResource_TypeNotFound_021() {
        // Loại chỗ chưa có trong bảng.
        SpaceResourceRequest request = new SpaceResourceRequest("MR-09", "Phòng", ResourceTypeCode.MEETING_ROOM, 6, null, null,
                null, null, null);
        log.input("request = {}", request);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.createResource(request));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP4, VP5
    @Test
    public void test_updateResource_SyncAmenities_022() {
        // Đang có ổ cắm ×2 và màn hình ×1 → sửa ổ cắm ×4, bỏ màn hình, bỏ khu vực, bảo trì.
        SpaceResource existing = existingResource();
        existing.addAmenity(new ResourceAmenity(outlet, 2));
        existing.addAmenity(new ResourceAmenity(monitor, 1));
        when(amenities.findByCodeIn(any())).thenReturn(List.of(outlet));
        SpaceResourceRequest request = new SpaceResourceRequest("HD-01", "Hot desk 1", ResourceTypeCode.HOT_DESK, 1,
                ResourceStatus.MAINTENANCE, null, null, null, List.of(new AmenityQuantity("POWER_OUTLET", 4)));
        log.input("id = {}, request = {}", 30L, request);

        SpaceResourceDetail detail = service.updateResource(30L, request);

        log.output("amenities = {}, outletQuantity = {}, zoneId = {}, status = {}", detail.amenities().size(),
                detail.amenities().get(0).quantity(), detail.zoneId(), detail.status());
        assertEquals(1, detail.amenities().size());
        assertEquals(4, detail.amenities().get(0).quantity());
        assertNull(detail.zoneId());
        assertEquals(ResourceStatus.MAINTENANCE, detail.status());
    }

    // Viewpoint: VP7
    @Test
    public void test_updateResource_NullAmenitiesKept_023() {
        // Không gửi danh sách tiện ích: giữ nguyên.
        SpaceResource existing = existingResource();
        existing.addAmenity(new ResourceAmenity(outlet, 2));
        SpaceResourceRequest request = new SpaceResourceRequest("HD-01", "Đổi tên", ResourceTypeCode.HOT_DESK, 1, null, 10L,
                null, null, null);
        log.input("id = {}, request = {}", 30L, request);

        SpaceResourceDetail detail = service.updateResource(30L, request);

        log.output("name = {}, amenities = {}", detail.name(), detail.amenities().size());
        assertEquals("Đổi tên", detail.name());
        assertEquals(1, detail.amenities().size());
    }

    // Viewpoint: VP3
    @Test
    public void test_updateResource_CodeTaken_024() {
        // Đổi sang mã của vị trí khác.
        existingResource();
        when(resources.existsByCodeIgnoreCase("HD-02")).thenReturn(true);
        SpaceResourceRequest request = new SpaceResourceRequest("HD-02", "Hot desk", ResourceTypeCode.HOT_DESK, 1, null, null,
                null, null, null);
        log.input("id = {}, request = {}", 30L, request);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.updateResource(30L, request));

        log.output("code = {}", thrown.code());
        assertEquals("CODE_ALREADY_EXISTS", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_deleteResource_NotFound_025() {
        // Không có vị trí.
        log.input("id = {}", 99L);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.deleteResource(99L));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // ---------- amenity ----------

    // Viewpoint: VP2
    @Test
    public void test_createAmenity_UppercaseCode_026() {
        // Mã tiện ích lưu chữ hoa.
        AmenityRequest request = new AmenityRequest(" whiteboard ", " Bảng trắng ");
        log.input("request = {}", request);

        AmenityDetail detail = service.createAmenity(request);

        log.output("code = {}, name = {}", detail.code(), detail.name());
        assertEquals("WHITEBOARD", detail.code());
        assertEquals("Bảng trắng", detail.name());
    }

    // Viewpoint: VP3
    @Test
    public void test_createAmenity_CodeExists_027() {
        // Mã đã có.
        when(amenities.existsByCodeIgnoreCase("MONITOR")).thenReturn(true);
        AmenityRequest request = new AmenityRequest("monitor", "Màn hình");
        log.input("request = {}", request);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.createAmenity(request));

        log.output("code = {}", thrown.code());
        assertEquals("CODE_ALREADY_EXISTS", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_updateAmenity_SameCode_028() {
        // Giữ mã, đổi tên: không kiểm tra trùng.
        when(amenities.findById(5L)).thenReturn(Optional.of(outlet));
        AmenityRequest request = new AmenityRequest("power_outlet", "Ổ điện");
        log.input("id = {}, request = {}", 5L, request);

        AmenityDetail detail = service.updateAmenity(5L, request);

        log.output("code = {}, name = {}", detail.code(), detail.name());
        assertEquals("POWER_OUTLET", detail.code());
        assertEquals("Ổ điện", detail.name());
        verify(amenities, never()).existsByCodeIgnoreCase(any());
    }

    // Viewpoint: VP3
    @Test
    public void test_updateAmenity_CodeTaken_029() {
        // Đổi sang mã đã có.
        when(amenities.findById(5L)).thenReturn(Optional.of(outlet));
        when(amenities.existsByCodeIgnoreCase("MONITOR")).thenReturn(true);
        AmenityRequest request = new AmenityRequest("MONITOR", "Màn hình");
        log.input("id = {}, request = {}", 5L, request);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.updateAmenity(5L, request));

        log.output("code = {}", thrown.code());
        assertEquals("CODE_ALREADY_EXISTS", thrown.code());
    }

    // ---------- price_rule ----------

    // Viewpoint: VP3, VP4
    @Test
    public void test_createPriceRule_AdjacentAllowed_030() {
        // Khung 09:00–18:00 sát khung 00:00–09:00 đã có (nửa mở): hợp lệ; khung cuối tuần trùng giờ không ảnh hưởng.
        existingRules(rule(1L, DayType.WEEKDAY, 0, 540), rule(2L, DayType.WEEKEND, 0, 1440));
        PriceRuleRequest request = new PriceRuleRequest(ResourceTypeCode.HOT_DESK, DayType.WEEKDAY, 540, 1080, 25_000, " Cao điểm ");
        log.input("request = {}", request);

        PriceRuleDetail detail = service.createPriceRule(request);

        log.output("startMinute = {}, endMinute = {}, label = {}", detail.startMinute(), detail.endMinute(), detail.label());
        assertEquals(540, detail.startMinute());
        assertEquals(1080, detail.endMinute());
        assertEquals("Cao điểm", detail.label());
    }

    // Viewpoint: VP3
    @Test
    public void test_createPriceRule_OverlapOneMinute_031() {
        // Chồng 1 phút với khung 00:00–09:00.
        existingRules(rule(1L, DayType.WEEKDAY, 0, 540));
        PriceRuleRequest request = new PriceRuleRequest(ResourceTypeCode.HOT_DESK, DayType.WEEKDAY, 539, 1080, 25_000, "Cao điểm");
        log.input("request = {}", request);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.createPriceRule(request));

        log.output("code = {}", thrown.code());
        assertEquals("PRICE_RULE_OVERLAP", thrown.code());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_createPriceRule_StartEqualsEnd_032() {
        // Phút bắt đầu bằng phút kết thúc.
        PriceRuleRequest request = new PriceRuleRequest(ResourceTypeCode.HOT_DESK, DayType.WEEKDAY, 600, 600, 25_000, "Sai");
        log.input("request = {}", request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.createPriceRule(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_TIME_RANGE", thrown.code());
    }

    // Viewpoint: VP3, VP5
    @Test
    public void test_updatePriceRule_OverlapWithItselfIgnored_033() {
        // Sửa giá của chính khung đó: không tính chồng với chính nó.
        PriceRule self = rule(1L, DayType.WEEKDAY, 0, 540);
        existingRules(self);
        when(priceRules.findById(1L)).thenReturn(Optional.of(self));
        PriceRuleRequest request = new PriceRuleRequest(ResourceTypeCode.HOT_DESK, DayType.WEEKDAY, 0, 540, 19_000, "Sáng");
        log.input("id = {}, request = {}", 1L, request);

        PriceRuleDetail detail = service.updatePriceRule(1L, request);

        log.output("id = {}", detail.id());
        assertEquals(Long.valueOf(1L), detail.id());
        verify(self).setPricePerBlock(19_000);
        verify(priceRules).save(self);
    }

    // Viewpoint: VP7
    @Test
    public void test_deletePriceRule_NotFound_034() {
        // Không có mức giá.
        log.input("id = {}", 99L);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.deletePriceRule(99L));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    private SpaceResource existingResource() {
        SpaceResource r = new SpaceResource("HD-01", "Hot desk 1", hotDesk, 1);
        r.setId(30L);
        r.setZone(zone);
        when(resources.findById(30L)).thenReturn(Optional.of(r));
        return r;
    }

    /** Mức giá có sẵn của HOT_DESK. Entity không có setId nên dùng mock. */
    private PriceRule rule(Long id, DayType dayType, int start, int end) {
        PriceRule rule = mock(PriceRule.class);
        when(rule.getId()).thenReturn(id);
        when(rule.getDayType()).thenReturn(dayType);
        when(rule.getStartMinute()).thenReturn(start);
        when(rule.getEndMinute()).thenReturn(end);
        when(rule.getResourceType()).thenReturn(hotDesk);
        return rule;
    }

    private void existingRules(PriceRule... rules) {
        when(priceRules.findByResourceTypeCode(ResourceTypeCode.HOT_DESK)).thenReturn(List.of(rules));
    }
}
