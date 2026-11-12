package com.cowork.business.service;

import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.config.PricingProperties;
import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.Customer;
import com.cowork.dataaccess.entity.MembershipPass;
import com.cowork.dataaccess.entity.MembershipPlan;
import com.cowork.dataaccess.entity.MembershipPlanResourceType;
import com.cowork.dataaccess.entity.MembershipUsage;
import com.cowork.dataaccess.entity.ResourceType;
import com.cowork.dataaccess.entity.SpaceResource;
import com.cowork.dataaccess.enums.MembershipPassStatus;
import com.cowork.dataaccess.enums.MembershipPlanStatus;
import com.cowork.dataaccess.enums.MembershipUsageStatus;
import com.cowork.dataaccess.enums.ResourceTypeCode;
import com.cowork.dataaccess.repository.MembershipPassRepository;
import com.cowork.dataaccess.repository.MembershipPlanRepository;
import com.cowork.dataaccess.repository.MembershipUsageRepository;
import com.cowork.dataaccess.repository.ResourceTypeRepository;
import com.cowork.dataaccess.valueobject.Money;
import com.cowork.dataaccess.valueobject.TimeRange;
import com.cowork.dto.CommerceDtos.MembershipPlanDetail;
import com.cowork.dto.CommerceDtos.MembershipPlanRequest;
import com.cowork.dto.CommerceDtos.MembershipSummary;
import com.cowork.dto.PriceQuote;
import com.cowork.support.MutableClock;
import com.cowork.support.TestCaseLogRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC05, UC21, BR-13, FR-PROMO-02, FR-AUTH-05: trừ / giữ / trả phút gói thành viên, đổi chỗ, hồ sơ, quản trị gói.
 * Viết theo {@code .claude/rules/unit-test.md}. Đơn mẫu: HOT_DESK 09:00–11:00 = 4 block × 30 phút, tiền thuê 200.000.
 */
public class MembershipServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 8, 0);
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 3, 9, 0);
    private static final TimeRange PERIOD = new TimeRange(START, START.plusHours(2));
    private static final PriceQuote RENTAL = new PriceQuote(4, 30, Money.vnd(200_000));
    private static final Long CUSTOMER_ID = 7L;
    private static final Long BOOKING_ID = 100L;
    private static final Long PASS_ID = 9L;

    @Rule
    public final TestCaseLogRule log = new TestCaseLogRule();

    private MembershipPlanRepository plans;
    private MembershipPassRepository passes;
    private MembershipUsageRepository usages;
    private ResourceTypeRepository resourceTypes;
    private PricingService pricing;
    private MembershipService service;
    private ResourceType hotDesk;
    private MembershipPlan hotDeskPlan;
    private Booking booking;

    @Before
    public void setUp() {
        MutableClock clock = new MutableClock(ZoneId.of("Asia/Ho_Chi_Minh"));
        clock.set(NOW);
        plans = mock(MembershipPlanRepository.class);
        passes = mock(MembershipPassRepository.class);
        usages = mock(MembershipUsageRepository.class);
        resourceTypes = mock(ResourceTypeRepository.class);
        pricing = mock(PricingService.class);
        service = new MembershipService(plans, passes, usages, resourceTypes, pricing, new PricingProperties(30, BigDecimal.ONE), clock);

        hotDesk = new ResourceType(ResourceTypeCode.HOT_DESK, "Hot desk", null);
        when(resourceTypes.findByCode(ResourceTypeCode.HOT_DESK)).thenReturn(Optional.of(hotDesk));
        hotDeskPlan = new MembershipPlan("Gói Hot Desk 20 giờ", 1200, 30, 1_000_000);
        hotDeskPlan.setId(1L);
        hotDeskPlan.getResourceTypes().add(new MembershipPlanResourceType(hotDeskPlan, hotDesk));
        when(plans.findById(1L)).thenReturn(Optional.of(hotDeskPlan));
        when(plans.save(any(MembershipPlan.class))).thenAnswer(call -> call.getArgument(0));

        Customer customer = new Customer();
        customer.setId(CUSTOMER_ID);
        booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setCustomer(customer);
        booking.setResource(new SpaceResource("HD-01", "Hot desk 1", hotDesk, 1));
        booking.setPeriod(PERIOD);
        when(usages.findByBookingId(BOOKING_ID)).thenReturn(List.of());
    }

    // ---------- deduct ----------

    // Viewpoint: VP3, VP4
    @Test
    public void test_deduct_CoversWholeBooking_001() {
        // Gói còn 1200 phút, đơn 120 phút: trừ 120, khấu trừ = toàn bộ tiền thuê, giữ phút (HELD).
        MembershipPass pass = usablePasses(pass(PASS_ID, hotDeskPlan, 1200)).get(0);
        log.input("booking = {}, rentalQuote = {}, now = {}", BOOKING_ID, RENTAL, NOW);

        Money deduction = service.deduct(booking, RENTAL, NOW);

        MembershipUsage usage = savedUsage();
        log.output("deduction = {}, remaining = {}, usageMinutes = {}, usageStatus = {}, usedAt = {}", deduction,
                pass.getRemainingMinutes(), usage.getMinutes(), usage.getStatus(), usage.getUsedAt());
        assertEquals(Money.vnd(200_000), deduction);
        assertEquals(1080, pass.getRemainingMinutes());
        assertEquals(120, usage.getMinutes());
        assertEquals(MembershipUsageStatus.HELD, usage.getStatus());
        assertEquals(NOW, usage.getUsedAt());
    }

    // Viewpoint: VP3
    @Test
    public void test_deduct_PartialRoundedToBlocks_002() {
        // Gói còn 75 phút: chỉ trừ 60 (bội số block), giá trị = giá 09:00–10:00; gói còn 15 phút, vẫn ACTIVE.
        MembershipPass pass = usablePasses(pass(PASS_ID, hotDeskPlan, 75)).get(0);
        when(pricing.quote(ResourceTypeCode.HOT_DESK, new TimeRange(START, START.plusMinutes(60))))
                .thenReturn(new PriceQuote(2, 30, Money.vnd(90_000)));
        log.input("booking = {}, rentalQuote = {}, now = {}", BOOKING_ID, RENTAL, NOW);

        Money deduction = service.deduct(booking, RENTAL, NOW);

        log.output("deduction = {}, remaining = {}, status = {}", deduction, pass.getRemainingMinutes(), pass.getStatus());
        assertEquals(Money.vnd(90_000), deduction);
        assertEquals(15, pass.getRemainingMinutes());
        assertEquals(MembershipPassStatus.ACTIVE, pass.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_deduct_UsesUpPass_003() {
        // Cận: gói còn đúng 120 phút → về 0, USED_UP.
        MembershipPass pass = usablePasses(pass(PASS_ID, hotDeskPlan, 120)).get(0);
        log.input("booking = {}, rentalQuote = {}, now = {}", BOOKING_ID, RENTAL, NOW);

        service.deduct(booking, RENTAL, NOW);

        log.output("remaining = {}, status = {}", pass.getRemainingMinutes(), pass.getStatus());
        assertEquals(0, pass.getRemainingMinutes());
        assertEquals(MembershipPassStatus.USED_UP, pass.getStatus());
    }

    // Viewpoint: VP4
    @Test
    public void test_deduct_NoPass_004() {
        // 0 gói còn dùng được.
        usablePasses();
        log.input("booking = {}, rentalQuote = {}, now = {}", BOOKING_ID, RENTAL, NOW);

        DomainException thrown = assertThrows(DomainException.class, () -> service.deduct(booking, RENTAL, NOW));

        log.output("code = {}", thrown.code());
        assertEquals("NO_USABLE_MEMBERSHIP", thrown.code());
        verify(usages, never()).save(any());
    }

    // Viewpoint: VP3, VP4
    @Test
    public void test_deduct_SkipsUnusablePasses_005() {
        // Nhiều gói: bỏ gói sai loại chỗ và gói còn 29 phút (< 1 block), lấy gói kế tiếp.
        MembershipPlan roomPlan = new MembershipPlan("Gói phòng họp", 600, 30, 2_000_000);
        roomPlan.getResourceTypes().add(new MembershipPlanResourceType(roomPlan,
                new ResourceType(ResourceTypeCode.MEETING_ROOM, "Phòng họp", null)));
        MembershipPass third = pass(12L, hotDeskPlan, 600);
        usablePasses(pass(10L, roomPlan, 600), pass(11L, hotDeskPlan, 29), third);
        log.input("booking = {}, rentalQuote = {}, now = {}", BOOKING_ID, RENTAL, NOW);

        service.deduct(booking, RENTAL, NOW);

        MembershipUsage usage = savedUsage();
        log.output("usedPass = {}", usage.getPass().getId());
        assertEquals(Long.valueOf(12L), usage.getPass().getId());
    }

    // Viewpoint: VP3
    @Test
    public void test_deduct_PassBelowOneBlock_006() {
        // Gói duy nhất còn 29 phút: không đủ 1 block.
        usablePasses(pass(PASS_ID, hotDeskPlan, 29));
        log.input("booking = {}, rentalQuote = {}, now = {}", BOOKING_ID, RENTAL, NOW);

        DomainException thrown = assertThrows(DomainException.class, () -> service.deduct(booking, RENTAL, NOW));

        log.output("code = {}", thrown.code());
        assertEquals("NO_USABLE_MEMBERSHIP", thrown.code());
    }

    // ---------- markUsed ----------

    // Viewpoint: VP3, VP4
    @Test
    public void test_markUsed_OnlyHeld_007() {
        // Lượt HELD → USED; lượt RELEASED giữ nguyên.
        MembershipUsage held = usage(pass(PASS_ID, hotDeskPlan, 0), 60, MembershipUsageStatus.HELD);
        MembershipUsage released = usage(pass(10L, hotDeskPlan, 0), 60, MembershipUsageStatus.RELEASED);
        when(usages.findByBookingId(BOOKING_ID)).thenReturn(List.of(held, released));
        log.input("booking = {}", BOOKING_ID);

        service.markUsed(booking);

        log.output("held = {}, released = {}", held.getStatus(), released.getStatus());
        assertEquals(MembershipUsageStatus.USED, held.getStatus());
        assertEquals(MembershipUsageStatus.RELEASED, released.getStatus());
    }

    // ---------- release ----------

    // Viewpoint: VP3
    @Test
    public void test_release_ReturnsMinutesAndReopensPass_008() {
        // Gói USED_UP còn hạn: trả 120 phút, mở lại ACTIVE.
        MembershipPass pass = pass(PASS_ID, hotDeskPlan, 0);
        pass.setStatus(MembershipPassStatus.USED_UP);
        MembershipUsage usage = usage(pass, 120, MembershipUsageStatus.USED);
        when(usages.findByBookingId(BOOKING_ID)).thenReturn(List.of(usage));
        log.input("booking = {}", BOOKING_ID);

        service.release(booking);

        log.output("usageStatus = {}, remaining = {}, passStatus = {}", usage.getStatus(), pass.getRemainingMinutes(), pass.getStatus());
        assertEquals(MembershipUsageStatus.RELEASED, usage.getStatus());
        assertEquals(120, pass.getRemainingMinutes());
        assertEquals(MembershipPassStatus.ACTIVE, pass.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_release_UsedUpPassExpired_009() {
        // Cận: gói USED_UP hết hạn đúng lúc này → EXPIRED.
        MembershipPass pass = pass(PASS_ID, hotDeskPlan, 0);
        pass.setStatus(MembershipPassStatus.USED_UP);
        pass.setExpiresAt(NOW);
        when(usages.findByBookingId(BOOKING_ID)).thenReturn(List.of(usage(pass, 60, MembershipUsageStatus.HELD)));
        log.input("booking = {}", BOOKING_ID);

        service.release(booking);

        log.output("remaining = {}, passStatus = {}", pass.getRemainingMinutes(), pass.getStatus());
        assertEquals(60, pass.getRemainingMinutes());
        assertEquals(MembershipPassStatus.EXPIRED, pass.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_release_AlreadyReleased_010() {
        // Gọi lại: không trả thêm.
        MembershipPass pass = pass(PASS_ID, hotDeskPlan, 60);
        when(usages.findByBookingId(BOOKING_ID)).thenReturn(List.of(usage(pass, 60, MembershipUsageStatus.RELEASED)));
        log.input("booking = {}", BOOKING_ID);

        service.release(booking);

        log.output("remaining = {}", pass.getRemainingMinutes());
        assertEquals(60, pass.getRemainingMinutes());
        verify(passes, never()).findByIdForUpdate(anyLong());
    }

    // Viewpoint: VP7
    @Test
    public void test_release_PassNotFound_011() {
        // Gói của lượt trừ không còn.
        MembershipPass ghost = new MembershipPass(null, hotDeskPlan, 0, NOW, NOW.plusDays(1));
        ghost.setId(99L);
        when(usages.findByBookingId(BOOKING_ID)).thenReturn(List.of(usage(ghost, 60, MembershipUsageStatus.HELD)));
        log.input("booking = {}", BOOKING_ID);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.release(booking));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // ---------- quoteChange ----------

    // Viewpoint: VP4
    @Test
    public void test_quoteChange_NoUsage_012() {
        // Đơn không dùng gói → 0.
        log.input("booking = {}, newType = {}, newPeriod = {}, newRentalQuote = {}", BOOKING_ID, ResourceTypeCode.HOT_DESK, PERIOD, RENTAL);

        Money value = service.quoteChange(booking, ResourceTypeCode.HOT_DESK, PERIOD, RENTAL);

        log.output("value = {}", value);
        assertEquals(Money.ZERO, value);
    }

    // Viewpoint: VP3
    @Test
    public void test_quoteChange_TypeNotCovered_013() {
        // Đổi sang phòng họp, gói chỉ cho hot desk.
        MembershipPass pass = pass(PASS_ID, hotDeskPlan, 0);
        when(usages.findByBookingId(BOOKING_ID)).thenReturn(List.of(usage(pass, 120, MembershipUsageStatus.USED)));
        log.input("booking = {}, newType = {}, newPeriod = {}, newRentalQuote = {}", BOOKING_ID, ResourceTypeCode.MEETING_ROOM, PERIOD, RENTAL);

        DomainException thrown = assertThrows(DomainException.class,
                () -> service.quoteChange(booking, ResourceTypeCode.MEETING_ROOM, PERIOD, RENTAL));

        log.output("code = {}", thrown.code());
        assertEquals("MEMBERSHIP_NOT_APPLICABLE", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_quoteChange_ShorterDurationFullyCovered_014() {
        // Đã trừ 120 phút, đơn mới 60 phút: phủ hết → bằng tiền thuê mới.
        MembershipPass pass = pass(PASS_ID, hotDeskPlan, 0);
        when(usages.findByBookingId(BOOKING_ID)).thenReturn(List.of(usage(pass, 120, MembershipUsageStatus.USED)));
        TimeRange newPeriod = new TimeRange(START, START.plusHours(1));
        PriceQuote newRental = new PriceQuote(2, 30, Money.vnd(90_000));
        log.input("booking = {}, newType = {}, newPeriod = {}, newRentalQuote = {}", BOOKING_ID, ResourceTypeCode.HOT_DESK, newPeriod, newRental);

        Money value = service.quoteChange(booking, ResourceTypeCode.HOT_DESK, newPeriod, newRental);

        log.output("value = {}", value);
        assertEquals(Money.vnd(90_000), value);
    }

    // Viewpoint: VP3
    @Test
    public void test_quoteChange_LongerDurationPartial_015() {
        // Đã trừ 60 phút, đơn mới 120 phút: chỉ phủ 60 phút đầu, không trừ thêm.
        MembershipPass pass = pass(PASS_ID, hotDeskPlan, 0);
        when(usages.findByBookingId(BOOKING_ID)).thenReturn(List.of(usage(pass, 60, MembershipUsageStatus.HELD)));
        when(pricing.quote(ResourceTypeCode.HOT_DESK, new TimeRange(START, START.plusMinutes(60))))
                .thenReturn(new PriceQuote(2, 30, Money.vnd(90_000)));
        log.input("booking = {}, newType = {}, newPeriod = {}, newRentalQuote = {}", BOOKING_ID, ResourceTypeCode.HOT_DESK, PERIOD, RENTAL);

        Money value = service.quoteChange(booking, ResourceTypeCode.HOT_DESK, PERIOD, RENTAL);

        log.output("value = {}", value);
        assertEquals(Money.vnd(90_000), value);
    }

    // ---------- applyChange ----------

    // Viewpoint: VP3
    @Test
    public void test_applyChange_NoExcess_016() {
        // Thời lượng mới bằng số phút đã trừ: không trả lại gì.
        MembershipPass pass = pass(PASS_ID, hotDeskPlan, 0);
        MembershipUsage usage = usage(pass, 120, MembershipUsageStatus.USED);
        when(usages.findByBookingId(BOOKING_ID)).thenReturn(List.of(usage));
        log.input("booking = {}, newRentalQuote = {}", BOOKING_ID, RENTAL);

        service.applyChange(booking, RENTAL);

        log.output("usageMinutes = {}, remaining = {}", usage.getMinutes(), pass.getRemainingMinutes());
        assertEquals(120, usage.getMinutes());
        assertEquals(0, pass.getRemainingMinutes());
    }

    // Viewpoint: VP3
    @Test
    public void test_applyChange_PartialExcess_017() {
        // Đã trừ 120, đơn mới 60 phút: bớt 60 phút của lượt, trả 60 phút cho gói.
        MembershipPass pass = pass(PASS_ID, hotDeskPlan, 0);
        pass.setStatus(MembershipPassStatus.USED_UP);
        MembershipUsage usage = usage(pass, 120, MembershipUsageStatus.USED);
        when(usages.findByBookingId(BOOKING_ID)).thenReturn(List.of(usage));
        PriceQuote newRental = new PriceQuote(2, 30, Money.vnd(90_000));
        log.input("booking = {}, newRentalQuote = {}", BOOKING_ID, newRental);

        service.applyChange(booking, newRental);

        log.output("usageMinutes = {}, usageStatus = {}, remaining = {}, passStatus = {}", usage.getMinutes(), usage.getStatus(),
                pass.getRemainingMinutes(), pass.getStatus());
        assertEquals(60, usage.getMinutes());
        assertEquals(MembershipUsageStatus.USED, usage.getStatus());
        assertEquals(60, pass.getRemainingMinutes());
        assertEquals(MembershipPassStatus.ACTIVE, pass.getStatus());
    }

    // Viewpoint: VP3, VP4
    @Test
    public void test_applyChange_ReleasesLastUsageFirst_018() {
        // Hai lượt 60 + 60, đơn mới 60 phút: lượt sau cùng bị trả hết (RELEASED), lượt đầu giữ nguyên.
        MembershipPass first = pass(PASS_ID, hotDeskPlan, 0);
        MembershipPass second = pass(10L, hotDeskPlan, 0);
        MembershipUsage firstUsage = usage(first, 60, MembershipUsageStatus.USED);
        MembershipUsage secondUsage = usage(second, 60, MembershipUsageStatus.USED);
        when(usages.findByBookingId(BOOKING_ID)).thenReturn(List.of(firstUsage, secondUsage));
        PriceQuote newRental = new PriceQuote(2, 30, Money.vnd(90_000));
        log.input("booking = {}, newRentalQuote = {}", BOOKING_ID, newRental);

        service.applyChange(booking, newRental);

        log.output("firstStatus = {}, secondStatus = {}, secondRemaining = {}", firstUsage.getStatus(), secondUsage.getStatus(),
                second.getRemainingMinutes());
        assertEquals(MembershipUsageStatus.USED, firstUsage.getStatus());
        assertEquals(MembershipUsageStatus.RELEASED, secondUsage.getStatus());
        assertEquals(60, second.getRemainingMinutes());
    }

    // ---------- summaryOf ----------

    // Viewpoint: VP4
    @Test
    public void test_summaryOf_NoPasses_019() {
        // Khách chưa mua gói.
        when(passes.findByCustomerIdOrderByExpiresAtAscIdAsc(CUSTOMER_ID)).thenReturn(List.of());
        log.input("customerId = {}", CUSTOMER_ID);

        MembershipSummary summary = service.summaryOf(CUSTOMER_ID);

        log.output("remainingMinutes = {}, passes = {}", summary.remainingMinutes(), summary.passes().size());
        assertEquals(0, summary.remainingMinutes());
        assertEquals(0, summary.passes().size());
    }

    // Viewpoint: VP3, VP4
    @Test
    public void test_summaryOf_MixedPasses_020() {
        // Gói ACTIVE quá hạn hiển thị EXPIRED, gói USED_UP không dùng được; chỉ cộng gói còn dùng được.
        MembershipPass expired = pass(1L, hotDeskPlan, 300);
        expired.setExpiresAt(NOW);
        MembershipPass usedUp = pass(2L, hotDeskPlan, 0);
        usedUp.setStatus(MembershipPassStatus.USED_UP);
        MembershipPass active = pass(3L, hotDeskPlan, 600);
        when(passes.findByCustomerIdOrderByExpiresAtAscIdAsc(CUSTOMER_ID)).thenReturn(List.of(expired, usedUp, active));
        log.input("customerId = {}", CUSTOMER_ID);

        MembershipSummary summary = service.summaryOf(CUSTOMER_ID);

        log.output("remainingMinutes = {}, firstStatus = {}, secondUsable = {}, thirdUsable = {}, planName = {}",
                summary.remainingMinutes(), summary.passes().get(0).status(), summary.passes().get(1).usable(),
                summary.passes().get(2).usable(), summary.passes().get(2).planName());
        assertEquals(600, summary.remainingMinutes());
        assertEquals(MembershipPassStatus.EXPIRED, summary.passes().get(0).status());
        assertFalse(summary.passes().get(1).usable());
        assertTrue(summary.passes().get(2).usable());
        assertEquals("Gói Hot Desk 20 giờ", summary.passes().get(2).planName());
    }

    // Viewpoint: VP3
    @Test
    public void test_summaryOf_ActiveWithZeroMinutes_021() {
        // Gói ACTIVE còn hạn nhưng 0 phút: không dùng được.
        MembershipPass pass = pass(1L, hotDeskPlan, 0);
        when(passes.findByCustomerIdOrderByExpiresAtAscIdAsc(CUSTOMER_ID)).thenReturn(List.of(pass));
        log.input("customerId = {}", CUSTOMER_ID);

        MembershipSummary summary = service.summaryOf(CUSTOMER_ID);

        log.output("usable = {}, remainingMinutes = {}", summary.passes().get(0).usable(), summary.remainingMinutes());
        assertFalse(summary.passes().get(0).usable());
        assertEquals(0, summary.remainingMinutes());
    }

    // ---------- quản trị gói ----------

    // Viewpoint: VP7
    @Test
    public void test_getPlan_NotFound_022() {
        // Không có gói.
        log.input("id = {}", 99L);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.getPlan(99L));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP4
    @Test
    public void test_createPlan_OneResourceType_023() {
        // Gói mới: ACTIVE, cắt khoảng trắng tên, gắn 1 loại chỗ.
        MembershipPlanRequest request = new MembershipPlanRequest("  Gói 10 giờ ", 600, 30, 500_000, null, List.of(ResourceTypeCode.HOT_DESK));
        log.input("request = {}", request);

        MembershipPlanDetail detail = service.createPlan(request);

        log.output("name = {}, status = {}, resourceTypes = {}", detail.name(), detail.status(), detail.resourceTypes());
        assertEquals("Gói 10 giờ", detail.name());
        assertEquals(MembershipPlanStatus.ACTIVE, detail.status());
        assertEquals(List.of(ResourceTypeCode.HOT_DESK), detail.resourceTypes());
    }

    // Viewpoint: VP7
    @Test
    public void test_createPlan_UnknownResourceType_024() {
        // Loại chỗ chưa có trong bảng resource_type.
        MembershipPlanRequest request = new MembershipPlanRequest("Gói phòng", 600, 30, 500_000, null, List.of(ResourceTypeCode.MEETING_ROOM));
        log.input("request = {}", request);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.createPlan(request));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP4, VP5
    @Test
    public void test_updatePlan_SyncResourceTypesAndStop_025() {
        // Bỏ HOT_DESK, thêm FIXED_DESK, ngưng bán.
        ResourceType fixedDesk = new ResourceType(ResourceTypeCode.FIXED_DESK, "Bàn cố định", null);
        when(resourceTypes.findByCode(ResourceTypeCode.FIXED_DESK)).thenReturn(Optional.of(fixedDesk));
        MembershipPlanRequest request = new MembershipPlanRequest("Gói mới", 900, 60, 800_000, MembershipPlanStatus.STOPPED,
                List.of(ResourceTypeCode.FIXED_DESK));
        log.input("id = {}, request = {}", 1L, request);

        MembershipPlanDetail detail = service.updatePlan(1L, request);

        log.output("totalMinutes = {}, status = {}, resourceTypes = {}", detail.totalMinutes(), detail.status(), detail.resourceTypes());
        assertEquals(900, detail.totalMinutes());
        assertEquals(MembershipPlanStatus.STOPPED, detail.status());
        assertEquals(List.of(ResourceTypeCode.FIXED_DESK), detail.resourceTypes());
    }

    // Viewpoint: VP7
    @Test
    public void test_deletePlan_Found_026() {
        // Xóa và flush ngay.
        log.input("id = {}", 1L);

        service.deletePlan(1L);

        log.output("deleted = {}", hotDeskPlan.getName());
        assertEquals("Gói Hot Desk 20 giờ", hotDeskPlan.getName());
        verify(plans).delete(hotDeskPlan);
        verify(plans).flush();
    }

    private MembershipPass pass(Long id, MembershipPlan plan, int remaining) {
        MembershipPass p = new MembershipPass(booking.getCustomer(), plan, remaining, NOW.minusDays(1), NOW.plusDays(29));
        p.setId(id);
        when(passes.findByIdForUpdate(id)).thenReturn(Optional.of(p));
        return p;
    }

    private List<MembershipPass> usablePasses(MembershipPass... list) {
        List<MembershipPass> result = List.of(list);
        when(passes.findUsableForUpdate(CUSTOMER_ID, START)).thenReturn(result);
        return result;
    }

    private MembershipUsage usage(MembershipPass pass, int minutes, MembershipUsageStatus status) {
        MembershipUsage u = new MembershipUsage(pass, booking, minutes);
        u.setStatus(status);
        return u;
    }

    private MembershipUsage savedUsage() {
        ArgumentCaptor<MembershipUsage> captor = ArgumentCaptor.forClass(MembershipUsage.class);
        verify(usages).save(captor.capture());
        return captor.getValue();
    }
}
