package com.cowork.business.service;

import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.config.PricingProperties;
import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.BookingCharge;
import com.cowork.dataaccess.entity.Customer;
import com.cowork.dataaccess.entity.ResourceType;
import com.cowork.dataaccess.entity.SpaceResource;
import com.cowork.dataaccess.entity.Voucher;
import com.cowork.dataaccess.entity.VoucherRedemption;
import com.cowork.dataaccess.entity.VoucherResourceType;
import com.cowork.dataaccess.enums.ChargeType;
import com.cowork.dataaccess.enums.DiscountType;
import com.cowork.dataaccess.enums.ResourceTypeCode;
import com.cowork.dataaccess.enums.VoucherRedemptionStatus;
import com.cowork.dataaccess.enums.VoucherStatus;
import com.cowork.dataaccess.repository.ResourceTypeRepository;
import com.cowork.dataaccess.repository.VoucherRedemptionRepository;
import com.cowork.dataaccess.repository.VoucherRepository;
import com.cowork.dataaccess.valueobject.Money;
import com.cowork.dataaccess.valueobject.TimeRange;
import com.cowork.dto.CommerceDtos.VoucherDetail;
import com.cowork.dto.CommerceDtos.VoucherRequest;
import com.cowork.dto.PriceQuote;
import com.cowork.support.TestCaseLogRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC05, UC21, BR-12, FR-PROMO-01, FR-PROMO-03: áp voucher, giữ / dùng / trả lượt, tính lại khi đổi chỗ, quản trị voucher.
 * Viết theo {@code .claude/rules/unit-test.md}. Đơn mẫu: HOT_DESK 09:00–11:00, tiền thuê 200.000 + add-on 50.000.
 */
public class VoucherServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 8, 0);
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 3, 9, 0);
    private static final TimeRange PERIOD = new TimeRange(START, START.plusHours(2));
    private static final String CODE = "SALE10";
    private static final Long VOUCHER_ID = 3L;
    private static final Long BOOKING_ID = 100L;
    private static final Long CUSTOMER_ID = 7L;

    @Rule
    public final TestCaseLogRule log = new TestCaseLogRule();

    private VoucherRepository vouchers;
    private VoucherRedemptionRepository redemptions;
    private ResourceTypeRepository resourceTypes;
    private PricingService pricing;
    private VoucherService service;
    private Voucher voucher;
    private Booking booking;
    private ResourceType hotDesk;

    @Before
    public void setUp() {
        vouchers = mock(VoucherRepository.class);
        redemptions = mock(VoucherRedemptionRepository.class);
        resourceTypes = mock(ResourceTypeRepository.class);
        pricing = mock(PricingService.class);
        service = new VoucherService(vouchers, redemptions, resourceTypes, pricing, new PricingProperties(30, BigDecimal.ONE));

        hotDesk = new ResourceType(ResourceTypeCode.HOT_DESK, "Hot desk", null);
        when(resourceTypes.findByCode(ResourceTypeCode.HOT_DESK)).thenReturn(Optional.of(hotDesk));
        voucher = new Voucher(CODE, "Giảm 10%", DiscountType.PERCENT, 10, NOW.minusDays(1), NOW.plusDays(1));
        voucher.setId(VOUCHER_ID);
        voucher.setPerCustomerLimit(1);
        when(vouchers.findByCodeForUpdate(CODE)).thenReturn(Optional.of(voucher));
        when(vouchers.findByIdForUpdate(VOUCHER_ID)).thenReturn(Optional.of(voucher));
        when(vouchers.findById(VOUCHER_ID)).thenReturn(Optional.of(voucher));
        when(vouchers.save(any(Voucher.class))).thenAnswer(call -> call.getArgument(0));
        when(redemptions.findByBookingId(anyLong())).thenReturn(Optional.empty());

        Customer customer = new Customer();
        customer.setId(CUSTOMER_ID);
        booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setCustomer(customer);
        booking.setResource(new SpaceResource("HD-01", "Hot desk 1", hotDesk, 1));
        booking.setPeriod(PERIOD);
        booking.getCharges().add(BookingCharge.of(booking, ChargeType.RENTAL, Money.vnd(200_000), PERIOD, NOW));
        booking.getCharges().add(BookingCharge.of(booking, ChargeType.ADDON, Money.vnd(50_000), PERIOD, NOW));
    }

    // ---------- redeem ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_redeem_Valid_001() {
        // Voucher hợp lệ: giảm 10% của 250.000, tăng lượt dùng, giữ lượt (HELD).
        String code = "  " + CODE + " ";
        log.input("code = {}, booking = {}, now = {}", code, BOOKING_ID, NOW);

        Money discount = service.redeem(code, booking, NOW);

        VoucherRedemption saved = savedRedemption();
        log.output("discount = {}, usedCount = {}, redemptionStatus = {}, redemptionAmount = {}, createdAt = {}", discount,
                voucher.getUsedCount(), saved.getStatus(), saved.getDiscountAmount(), saved.getCreatedAt());
        assertEquals(Money.vnd(25_000), discount);
        assertEquals(1, voucher.getUsedCount());
        assertEquals(VoucherRedemptionStatus.HELD, saved.getStatus());
        assertEquals(25_000L, saved.getDiscountAmount());
        assertEquals(NOW, saved.getCreatedAt());
    }

    // Viewpoint: VP7
    @Test
    public void test_redeem_NotFound_002() {
        // Mã không tồn tại.
        log.input("code = {}, booking = {}, now = {}", "KHONGCO", BOOKING_ID, NOW);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.redeem("KHONGCO", booking, NOW));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_redeem_Paused_003() {
        // Voucher tạm dừng.
        voucher.setStatus(VoucherStatus.PAUSED);
        log.input("code = {}, booking = {}, now = {}", CODE, BOOKING_ID, NOW);

        DomainException thrown = assertThrows(DomainException.class, () -> service.redeem(CODE, booking, NOW));

        log.output("code = {}", thrown.code());
        assertEquals("VOUCHER_INACTIVE", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_redeem_ExactValidFrom_004() {
        // Cận dưới: đúng lúc bắt đầu hiệu lực.
        voucher.setValidFrom(NOW);
        log.input("code = {}, booking = {}, now = {}", CODE, BOOKING_ID, NOW);

        Money discount = service.redeem(CODE, booking, NOW);

        log.output("discount = {}", discount);
        assertEquals(Money.vnd(25_000), discount);
    }

    // Viewpoint: VP3
    @Test
    public void test_redeem_OneSecondBeforeValidFrom_005() {
        // Còn 1 giây mới tới ngày hiệu lực.
        voucher.setValidFrom(NOW.plusSeconds(1));
        log.input("code = {}, booking = {}, now = {}", CODE, BOOKING_ID, NOW);

        DomainException thrown = assertThrows(DomainException.class, () -> service.redeem(CODE, booking, NOW));

        log.output("code = {}", thrown.code());
        assertEquals("VOUCHER_EXPIRED", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_redeem_ExactValidTo_006() {
        // Cận trên: đúng lúc hết hạn (khoảng nửa mở) → hết hạn.
        voucher.setValidTo(NOW);
        log.input("code = {}, booking = {}, now = {}", CODE, BOOKING_ID, NOW);

        DomainException thrown = assertThrows(DomainException.class, () -> service.redeem(CODE, booking, NOW));

        log.output("code = {}", thrown.code());
        assertEquals("VOUCHER_EXPIRED", thrown.code());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_redeem_TotalLimitReached_007() {
        // Đã dùng đủ tổng lượt.
        voucher.setTotalLimit(5);
        voucher.setUsedCount(5);
        log.input("code = {}, booking = {}, now = {}", CODE, BOOKING_ID, NOW);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.redeem(CODE, booking, NOW));

        log.output("code = {}", thrown.code());
        assertEquals("VOUCHER_USED_UP", thrown.code());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_redeem_LastTotalSlot_008() {
        // Còn đúng 1 lượt cuối.
        voucher.setTotalLimit(5);
        voucher.setUsedCount(4);
        log.input("code = {}, booking = {}, now = {}", CODE, BOOKING_ID, NOW);

        service.redeem(CODE, booking, NOW);

        log.output("usedCount = {}", voucher.getUsedCount());
        assertEquals(5, voucher.getUsedCount());
    }

    // Viewpoint: VP3
    @Test
    public void test_redeem_CustomerLimitReached_009() {
        // Khách đã dùng hết lượt của mình.
        when(redemptions.countByVoucherIdAndCustomerIdAndStatusIn(eq(VOUCHER_ID), eq(CUSTOMER_ID), any())).thenReturn(1L);
        log.input("code = {}, booking = {}, now = {}", CODE, BOOKING_ID, NOW);

        DomainException thrown = assertThrows(DomainException.class, () -> service.redeem(CODE, booking, NOW));

        log.output("code = {}", thrown.code());
        assertEquals("VOUCHER_CUSTOMER_LIMIT", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_redeem_TypeNotAllowed_010() {
        // Voucher chỉ cho phòng họp.
        voucher.getResourceTypes().add(new VoucherResourceType(voucher, new ResourceType(ResourceTypeCode.MEETING_ROOM, "Phòng họp", null)));
        log.input("code = {}, booking = {}, now = {}", CODE, BOOKING_ID, NOW);

        DomainException thrown = assertThrows(DomainException.class, () -> service.redeem(CODE, booking, NOW));

        log.output("code = {}", thrown.code());
        assertEquals("VOUCHER_NOT_APPLICABLE", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_redeem_TypeInList_011() {
        // Loại chỗ có trong danh sách áp dụng.
        voucher.getResourceTypes().add(new VoucherResourceType(voucher, hotDesk));
        log.input("code = {}, booking = {}, now = {}", CODE, BOOKING_ID, NOW);

        Money discount = service.redeem(CODE, booking, NOW);

        log.output("discount = {}", discount);
        assertEquals(Money.vnd(25_000), discount);
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_redeem_MinOrderOneMore_012() {
        // Đơn thiếu 1 đồng so với giá trị tối thiểu.
        voucher.setMinOrderAmount(250_001);
        log.input("code = {}, booking = {}, now = {}", CODE, BOOKING_ID, NOW);

        DomainException thrown = assertThrows(DomainException.class, () -> service.redeem(CODE, booking, NOW));

        log.output("code = {}", thrown.code());
        assertEquals("VOUCHER_MIN_ORDER", thrown.code());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_redeem_MinOrderExact_013() {
        // Cận: đơn đúng bằng giá trị tối thiểu.
        voucher.setMinOrderAmount(250_000);
        log.input("code = {}, booking = {}, now = {}", CODE, BOOKING_ID, NOW);

        Money discount = service.redeem(CODE, booking, NOW);

        log.output("discount = {}", discount);
        assertEquals(Money.vnd(25_000), discount);
    }

    // Viewpoint: VP3
    @Test
    public void test_redeem_ZeroDiscount_014() {
        // Phút miễn phí nhỏ hơn 1 block → giảm 0 → không áp được.
        voucher.setDiscountType(DiscountType.FREE_MINUTES);
        voucher.setDiscountValue(29);
        log.input("code = {}, booking = {}, now = {}", CODE, BOOKING_ID, NOW);

        DomainException thrown = assertThrows(DomainException.class, () -> service.redeem(CODE, booking, NOW));

        log.output("code = {}, usedCount = {}", thrown.code(), voucher.getUsedCount());
        assertEquals("VOUCHER_NOT_APPLICABLE", thrown.code());
        assertEquals(0, voucher.getUsedCount());
    }

    // ---------- markUsed ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_markUsed_Held_015() {
        // Lượt đang giữ → đã dùng.
        VoucherRedemption redemption = redemption(VoucherRedemptionStatus.HELD);
        log.input("booking = {}", BOOKING_ID);

        service.markUsed(booking);

        log.output("status = {}", redemption.getStatus());
        assertEquals(VoucherRedemptionStatus.USED, redemption.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_markUsed_Released_016() {
        // Lượt đã trả: giữ nguyên.
        VoucherRedemption redemption = redemption(VoucherRedemptionStatus.RELEASED);
        log.input("booking = {}", BOOKING_ID);

        service.markUsed(booking);

        log.output("status = {}", redemption.getStatus());
        assertEquals(VoucherRedemptionStatus.RELEASED, redemption.getStatus());
    }

    // ---------- release ----------

    // Viewpoint: VP3
    @Test
    public void test_release_Held_017() {
        // Trả lượt đang giữ: RELEASED, giảm lượt dùng.
        VoucherRedemption redemption = redemption(VoucherRedemptionStatus.HELD);
        voucher.setUsedCount(3);
        log.input("booking = {}", BOOKING_ID);

        service.release(booking);

        log.output("status = {}, usedCount = {}", redemption.getStatus(), voucher.getUsedCount());
        assertEquals(VoucherRedemptionStatus.RELEASED, redemption.getStatus());
        assertEquals(2, voucher.getUsedCount());
    }

    // Viewpoint: VP3
    @Test
    public void test_release_Used_018() {
        // Đơn đã xác nhận bị hủy: trả lượt đã dùng.
        VoucherRedemption redemption = redemption(VoucherRedemptionStatus.USED);
        voucher.setUsedCount(1);
        log.input("booking = {}", BOOKING_ID);

        service.release(booking);

        log.output("status = {}, usedCount = {}", redemption.getStatus(), voucher.getUsedCount());
        assertEquals(VoucherRedemptionStatus.RELEASED, redemption.getStatus());
        assertEquals(0, voucher.getUsedCount());
    }

    // Viewpoint: VP3
    @Test
    public void test_release_AlreadyReleased_019() {
        // Gọi lại lần hai: không trả thêm.
        redemption(VoucherRedemptionStatus.RELEASED);
        voucher.setUsedCount(2);
        log.input("booking = {}", BOOKING_ID);

        service.release(booking);

        log.output("usedCount = {}", voucher.getUsedCount());
        assertEquals(2, voucher.getUsedCount());
        verify(vouchers, never()).findByIdForUpdate(anyLong());
    }

    // Viewpoint: VP1
    @Test
    public void test_release_UsedCountZero_020() {
        // Cận dưới: lượt dùng đã là 0 thì không âm.
        redemption(VoucherRedemptionStatus.HELD);
        voucher.setUsedCount(0);
        log.input("booking = {}", BOOKING_ID);

        service.release(booking);

        log.output("usedCount = {}", voucher.getUsedCount());
        assertEquals(0, voucher.getUsedCount());
    }

    // ---------- quoteChange / applyChange ----------

    // Viewpoint: VP7
    @Test
    public void test_quoteChange_NoVoucher_021() {
        // Đơn không có voucher → 0.
        Money subtotal = Money.vnd(300_000);
        log.input("booking = {}, newType = {}, newPeriod = {}, newSubtotal = {}", BOOKING_ID, ResourceTypeCode.MEETING_ROOM, PERIOD, subtotal);

        Money discount = service.quoteChange(booking, ResourceTypeCode.MEETING_ROOM, PERIOD, subtotal);

        log.output("discount = {}", discount);
        assertEquals(Money.ZERO, discount);
    }

    // Viewpoint: VP3
    @Test
    public void test_quoteChange_PercentOfNewSubtotal_022() {
        // Voucher 10% tính lại trên tạm tính mới, chưa ghi vào lượt.
        VoucherRedemption redemption = redemption(VoucherRedemptionStatus.USED);
        Money subtotal = Money.vnd(300_000);
        log.input("booking = {}, newType = {}, newPeriod = {}, newSubtotal = {}", BOOKING_ID, ResourceTypeCode.HOT_DESK, PERIOD, subtotal);

        Money discount = service.quoteChange(booking, ResourceTypeCode.HOT_DESK, PERIOD, subtotal);

        log.output("discount = {}, redemptionAmount = {}", discount, redemption.getDiscountAmount());
        assertEquals(Money.vnd(30_000), discount);
        assertEquals(25_000L, redemption.getDiscountAmount());
    }

    // Viewpoint: VP3
    @Test
    public void test_quoteChange_TypeNotAllowed_023() {
        // Voucher chỉ cho hot desk, đổi sang phòng họp.
        voucher.getResourceTypes().add(new VoucherResourceType(voucher, hotDesk));
        redemption(VoucherRedemptionStatus.USED);
        Money subtotal = Money.vnd(300_000);
        log.input("booking = {}, newType = {}, newPeriod = {}, newSubtotal = {}", BOOKING_ID, ResourceTypeCode.MEETING_ROOM, PERIOD, subtotal);

        DomainException thrown = assertThrows(DomainException.class,
                () -> service.quoteChange(booking, ResourceTypeCode.MEETING_ROOM, PERIOD, subtotal));

        log.output("code = {}", thrown.code());
        assertEquals("VOUCHER_NOT_APPLICABLE", thrown.code());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_quoteChange_BelowMinOrder_024() {
        // Chỗ mới rẻ hơn giá trị tối thiểu của voucher.
        voucher.setMinOrderAmount(250_000);
        redemption(VoucherRedemptionStatus.USED);
        Money subtotal = Money.vnd(249_999);
        log.input("booking = {}, newType = {}, newPeriod = {}, newSubtotal = {}", BOOKING_ID, ResourceTypeCode.HOT_DESK, PERIOD, subtotal);

        DomainException thrown = assertThrows(DomainException.class,
                () -> service.quoteChange(booking, ResourceTypeCode.HOT_DESK, PERIOD, subtotal));

        log.output("code = {}", thrown.code());
        assertEquals("VOUCHER_MIN_ORDER", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_quoteChange_ReleasedIgnored_025() {
        // Lượt đã trả: coi như không có voucher.
        redemption(VoucherRedemptionStatus.RELEASED);
        Money subtotal = Money.vnd(300_000);
        log.input("booking = {}, newType = {}, newPeriod = {}, newSubtotal = {}", BOOKING_ID, ResourceTypeCode.HOT_DESK, PERIOD, subtotal);

        Money discount = service.quoteChange(booking, ResourceTypeCode.HOT_DESK, PERIOD, subtotal);

        log.output("discount = {}", discount);
        assertEquals(Money.ZERO, discount);
    }

    // Viewpoint: VP5
    @Test
    public void test_applyChange_WritesDiscount_026() {
        // Ghi số tiền giảm mới vào lượt.
        VoucherRedemption redemption = redemption(VoucherRedemptionStatus.USED);
        Money discount = Money.vnd(30_000);
        log.input("booking = {}, discount = {}", BOOKING_ID, discount);

        service.applyChange(booking, discount);

        log.output("discountAmount = {}", redemption.getDiscountAmount());
        assertEquals(30_000L, redemption.getDiscountAmount());
    }

    // ---------- discountFor ----------

    // Viewpoint: VP3
    @Test
    public void test_discountFor_PercentCapped_027() {
        // 10% của 1.000.000 = 100.000, trần 50.000.
        voucher.setMaxDiscountAmount(50_000L);
        Money subtotal = Money.vnd(1_000_000);
        log.input("voucher = {}, type = {}, period = {}, subtotal = {}", CODE, ResourceTypeCode.HOT_DESK, PERIOD, subtotal);

        Money discount = service.discountFor(voucher, ResourceTypeCode.HOT_DESK, PERIOD, subtotal);

        log.output("discount = {}", discount);
        assertEquals(Money.vnd(50_000), discount);
    }

    // Viewpoint: VP3
    @Test
    public void test_discountFor_PercentRoundsDown_028() {
        // 10% của 99.999 làm tròn xuống 9.999.
        Money subtotal = Money.vnd(99_999);
        log.input("voucher = {}, type = {}, period = {}, subtotal = {}", CODE, ResourceTypeCode.HOT_DESK, PERIOD, subtotal);

        Money discount = service.discountFor(voucher, ResourceTypeCode.HOT_DESK, PERIOD, subtotal);

        log.output("discount = {}", discount);
        assertEquals(Money.vnd(9_999), discount);
    }

    // Viewpoint: VP3
    @Test
    public void test_discountFor_AmountCappedBySubtotal_029() {
        // Giảm 100.000 nhưng đơn chỉ 80.000.
        voucher.setDiscountType(DiscountType.AMOUNT);
        voucher.setDiscountValue(100_000);
        Money subtotal = Money.vnd(80_000);
        log.input("voucher = {}, type = {}, period = {}, subtotal = {}", CODE, ResourceTypeCode.HOT_DESK, PERIOD, subtotal);

        Money discount = service.discountFor(voucher, ResourceTypeCode.HOT_DESK, PERIOD, subtotal);

        log.output("discount = {}", discount);
        assertEquals(Money.vnd(80_000), discount);
    }

    // Viewpoint: VP3
    @Test
    public void test_discountFor_FreeMinutes_030() {
        // 75 phút miễn phí → làm tròn xuống 60 phút = giá 09:00–10:00.
        voucher.setDiscountType(DiscountType.FREE_MINUTES);
        voucher.setDiscountValue(75);
        when(pricing.quote(ResourceTypeCode.HOT_DESK, new TimeRange(START, START.plusMinutes(60))))
                .thenReturn(new PriceQuote(2, 30, Money.vnd(70_000)));
        Money subtotal = Money.vnd(250_000);
        log.input("voucher = {}, type = {}, period = {}, subtotal = {}", CODE, ResourceTypeCode.HOT_DESK, PERIOD, subtotal);

        Money discount = service.discountFor(voucher, ResourceTypeCode.HOT_DESK, PERIOD, subtotal);

        log.output("discount = {}", discount);
        assertEquals(Money.vnd(70_000), discount);
    }

    // Viewpoint: VP3
    @Test
    public void test_discountFor_FreeMinutesOverBooking_031() {
        // Phút miễn phí dài hơn đơn: chỉ tính tới hết đơn (120 phút).
        voucher.setDiscountType(DiscountType.FREE_MINUTES);
        voucher.setDiscountValue(600);
        when(pricing.quote(ResourceTypeCode.HOT_DESK, PERIOD)).thenReturn(new PriceQuote(4, 30, Money.vnd(200_000)));
        Money subtotal = Money.vnd(250_000);
        log.input("voucher = {}, type = {}, period = {}, subtotal = {}", CODE, ResourceTypeCode.HOT_DESK, PERIOD, subtotal);

        Money discount = service.discountFor(voucher, ResourceTypeCode.HOT_DESK, PERIOD, subtotal);

        log.output("discount = {}", discount);
        assertEquals(Money.vnd(200_000), discount);
    }

    // ---------- quản trị ----------

    // Viewpoint: VP7
    @Test
    public void test_get_NotFound_032() {
        // Không có voucher.
        log.input("id = {}", 99L);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.get(99L));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP2, VP4
    @Test
    public void test_create_WithOneResourceType_033() {
        // Mã viết hoa, ACTIVE, 1 loại chỗ áp dụng.
        VoucherRequest request = request(" new10 ", DiscountType.PERCENT, 100, null, List.of(ResourceTypeCode.HOT_DESK));
        log.input("request = {}", request);

        VoucherDetail detail = service.create(request);

        log.output("code = {}, status = {}, resourceTypes = {}", detail.code(), detail.status(), detail.resourceTypes());
        assertEquals("NEW10", detail.code());
        assertEquals(VoucherStatus.ACTIVE, detail.status());
        assertEquals(List.of(ResourceTypeCode.HOT_DESK), detail.resourceTypes());
    }

    // Viewpoint: VP3
    @Test
    public void test_create_CodeExists_034() {
        // Mã đã có.
        when(vouchers.existsByCodeIgnoreCase(CODE)).thenReturn(true);
        VoucherRequest request = request(CODE, DiscountType.AMOUNT, 10_000, null, null);
        log.input("request = {}", request);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.create(request));

        log.output("code = {}", thrown.code());
        assertEquals("CODE_ALREADY_EXISTS", thrown.code());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_create_PercentOver100_035() {
        // Giảm 101%.
        VoucherRequest request = request("P101", DiscountType.PERCENT, 101, null, null);
        log.input("request = {}", request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.create(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_DISCOUNT", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_create_ValidToEqualsValidFrom_036() {
        // Ngày hết hạn trùng ngày bắt đầu.
        VoucherRequest request = new VoucherRequest("SAME", "Sai ngày", DiscountType.AMOUNT, 10_000, 0, null, NOW, NOW,
                null, 1, null, null);
        log.input("request = {}", request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.create(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_TIME_RANGE", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_create_UnknownResourceType_037() {
        // Loại chỗ chưa có trong bảng resource_type.
        VoucherRequest request = request("ROOM", DiscountType.AMOUNT, 10_000, null, List.of(ResourceTypeCode.MEETING_ROOM));
        log.input("request = {}", request);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.create(request));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_update_TotalLimitBelowUsed_038() {
        // Tổng lượt mới nhỏ hơn số đã dùng 1.
        voucher.setUsedCount(5);
        VoucherRequest request = request(CODE, DiscountType.PERCENT, 10, 4, null);
        log.input("id = {}, request = {}", VOUCHER_ID, request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.update(VOUCHER_ID, request));

        log.output("code = {}", thrown.code());
        assertEquals("VOUCHER_LIMIT_BELOW_USED", thrown.code());
    }

    // Viewpoint: VP1, VP3, VP4
    @Test
    public void test_update_TotalLimitEqualsUsedAndClearTypes_039() {
        // Cận: tổng lượt bằng số đã dùng; bỏ hết loại chỗ (áp mọi loại).
        voucher.setUsedCount(5);
        voucher.getResourceTypes().add(new VoucherResourceType(voucher, hotDesk));
        VoucherRequest request = request(CODE, DiscountType.PERCENT, 10, 5, List.of());
        log.input("id = {}, request = {}", VOUCHER_ID, request);

        VoucherDetail detail = service.update(VOUCHER_ID, request);

        log.output("totalLimit = {}, resourceTypes = {}", detail.totalLimit(), detail.resourceTypes());
        assertEquals(Integer.valueOf(5), detail.totalLimit());
        assertEquals(List.of(), detail.resourceTypes());
    }

    // Viewpoint: VP3
    @Test
    public void test_update_CodeTakenByOther_040() {
        // Đổi sang mã của voucher khác.
        when(vouchers.existsByCodeIgnoreCase("OTHER")).thenReturn(true);
        VoucherRequest request = request("other", DiscountType.PERCENT, 10, null, null);
        log.input("id = {}, request = {}", VOUCHER_ID, request);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.update(VOUCHER_ID, request));

        log.output("code = {}", thrown.code());
        assertEquals("CODE_ALREADY_EXISTS", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_delete_Found_041() {
        // Xóa và flush ngay.
        log.input("id = {}", VOUCHER_ID);

        service.delete(VOUCHER_ID);

        log.output("deleted = {}", voucher.getCode());
        assertEquals(CODE, voucher.getCode());
        verify(vouchers).delete(voucher);
        verify(vouchers).flush();
    }

    private VoucherRedemption redemption(VoucherRedemptionStatus status) {
        VoucherRedemption r = new VoucherRedemption(voucher, booking, booking.getCustomer(), 25_000);
        r.setStatus(status);
        when(redemptions.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(r));
        return r;
    }

    private VoucherRedemption savedRedemption() {
        ArgumentCaptor<VoucherRedemption> captor = ArgumentCaptor.forClass(VoucherRedemption.class);
        verify(redemptions).save(captor.capture());
        return captor.getValue();
    }

    private static VoucherRequest request(String code, DiscountType type, long value, Integer totalLimit,
                                          List<ResourceTypeCode> types) {
        return new VoucherRequest(code, " Khuyến mãi ", type, value, 0, null, NOW, NOW.plusDays(30), totalLimit, 1, null, types);
    }
}
