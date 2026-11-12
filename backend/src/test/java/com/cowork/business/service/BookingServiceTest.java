package com.cowork.business.service;

import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.business.service.AddonService.AddonLine;
import com.cowork.business.service.BookingService.RescheduleResult;
import com.cowork.config.BookingProperties;
import com.cowork.dataaccess.entity.Addon;
import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.BookingAddon;
import com.cowork.dataaccess.entity.BookingCharge;
import com.cowork.dataaccess.entity.Branch;
import com.cowork.dataaccess.entity.Customer;
import com.cowork.dataaccess.entity.ResourceType;
import com.cowork.dataaccess.entity.SpaceResource;
import com.cowork.dataaccess.entity.Zone;
import com.cowork.dataaccess.enums.BookingStatus;
import com.cowork.dataaccess.enums.ChargeType;
import com.cowork.dataaccess.enums.ResourceTypeCode;
import com.cowork.dataaccess.enums.StaffRole;
import com.cowork.dataaccess.repository.BookingRepository;
import com.cowork.dataaccess.repository.SpaceResourceRepository;
import com.cowork.dataaccess.valueobject.Money;
import com.cowork.dataaccess.valueobject.TimeRange;
import com.cowork.dto.Actor;
import com.cowork.dto.BookingDtos.AddonSelection;
import com.cowork.dto.BookingDtos.BookingFilter;
import com.cowork.dto.BookingDtos.ContactRequest;
import com.cowork.dto.BookingDtos.HoldBookingRequest;
import com.cowork.dto.BookingDtos.RescheduleRequest;
import com.cowork.dto.PriceQuote;
import com.cowork.support.MutableClock;
import com.cowork.support.TestCaseLogRule;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC03, UC04, UC06, UC07, UC08, UC09, UC16, UC24, UC25: nghiệp vụ đơn đặt chỗ. Viết theo {@code .claude/rules/unit-test.md}.
 * Các service phụ thuộc và repository được giả lập, nên mã lỗi của chúng là giá trị test tự đặt để kiểm tra BookingService xử lý đúng.
 */
public class BookingServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 8, 0);
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 3, 9, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 3, 11, 0);
    private static final LocalDateTime NEW_START = LocalDateTime.of(2026, 11, 3, 13, 0);
    private static final LocalDateTime NEW_END = LocalDateTime.of(2026, 11, 3, 15, 0);
    private static final LocalDateTime CHANGE_DEADLINE = START.minusHours(2);
    private static final Long RESOURCE_ID = 10L;
    private static final Long OTHER_RESOURCE_ID = 11L;
    private static final Long BOOKING_ID = 100L;
    private static final Long CUSTOMER_ID = 7L;
    private static final String QR_TOKEN = "qr-token";
    private static final long RENTAL = 200_000L;
    private static final Actor OWNER = Actor.customer(CUSTOMER_ID);
    private static final Actor OTHER_CUSTOMER = Actor.customer(8L);
    private static final Actor STAFF = Actor.staff(5L, StaffRole.RECEPTIONIST);
    private static final ContactRequest CONTACT = new ContactRequest("Lê C", "0911222333", null);
    /** Mỗi ký tự chiếm 1 byte UTF-8: chữ cái, số, ký hiệu. */
    private static final String TEXT_1_BYTE = "Sale10-A!";
    /** Mỗi ký tự chiếm 2 byte UTF-8. */
    private static final String TEXT_2_BYTE = "àêô";
    /** Mỗi ký tự chiếm 3 byte UTF-8. */
    private static final String TEXT_3_BYTE = "ệặữ";

    @Rule
    public final TestCaseLogRule log = new TestCaseLogRule();

    private MutableClock clock;
    private BookingRepository bookings;
    private SpaceResourceRepository resources;
    private ResourceService resourceService;
    private PricingService pricing;
    private CustomerService customers;
    private NotificationService notifications;
    private AddonService addons;
    private VoucherService vouchers;
    private MembershipService memberships;
    private RefundService refunds;
    private BookingWindowPolicy windowPolicy;
    private BookingService service;
    private Branch branch;
    private SpaceResource resource;
    private Customer customer;

    @Before
    public void setUp() {
        clock = new MutableClock(ZoneId.of("Asia/Ho_Chi_Minh"));
        clock.set(NOW);
        bookings = mock(BookingRepository.class);
        resources = mock(SpaceResourceRepository.class);
        resourceService = mock(ResourceService.class);
        pricing = mock(PricingService.class);
        customers = mock(CustomerService.class);
        notifications = mock(NotificationService.class);
        addons = mock(AddonService.class);
        vouchers = mock(VoucherService.class);
        memberships = mock(MembershipService.class);
        refunds = mock(RefundService.class);
        windowPolicy = mock(BookingWindowPolicy.class);
        BookingProperties properties = new BookingProperties(Duration.ofMinutes(10), Duration.ofMinutes(15),
                Duration.ofMinutes(5), Duration.ofHours(1), Duration.ofHours(2), Duration.ofMinutes(30), 50);
        service = new BookingService(bookings, resources, resourceService, pricing, customers, notifications, addons,
                vouchers, memberships, refunds, clock, properties, windowPolicy);

        branch = new Branch();
        branch.setId(1L);
        resource = new SpaceResource("HD-01", "Hot desk 1", new ResourceType(ResourceTypeCode.HOT_DESK, "Hot desk", "Bàn"), 4);
        resource.setId(RESOURCE_ID);
        resource.setZone(new Zone(branch, "Tầng 1", 1, null));
        customer = new Customer();
        customer.setId(CUSTOMER_ID);

        when(resources.findByIdForUpdate(RESOURCE_ID)).thenReturn(Optional.of(resource));
        when(windowPolicy.validate(any(), any(), any())).thenAnswer(call -> new TimeRange(call.getArgument(0), call.getArgument(1)));
        when(pricing.quote(any(), any())).thenReturn(quote(RENTAL));
        when(customers.get(CUSTOMER_ID)).thenReturn(customer);
        when(customers.registerOrUpdate(any())).thenReturn(customer);
        when(vouchers.quoteChange(any(), any(), any(), any())).thenReturn(Money.ZERO);
        when(memberships.quoteChange(any(), any(), any(), any())).thenReturn(Money.ZERO);
        when(bookings.save(any(Booking.class))).thenAnswer(call -> {
            Booking b = call.getArgument(0);
            if (b.getId() == null) {
                b.setId(BOOKING_ID);
            }
            return b;
        });
    }

    // ---------- hold(request) ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_hold_GuestNewBooking_001() {
        // Khách vãng lai nhập liên hệ: giữ chỗ 10 phút, có mã đơn, mã QR và một dòng tiền thuê.
        HoldBookingRequest request = new HoldBookingRequest(RESOURCE_ID, START, END, 2, CONTACT);
        log.input("request = {}", request);

        Booking booking = service.hold(request);

        log.output("status = {}, holdExpiresAt = {}, createdAt = {}, total = {}, partySize = {}, customer = {}, codeLength = {}, qrLength = {}, historySize = {}",
                booking.getStatus(), booking.getHoldExpiresAt(), booking.getCreatedAt(), booking.getTotal(), booking.getPartySize(),
                booking.getCustomer().getId(), booking.getCode().length(), booking.getQrToken().length(), booking.getStatusHistory().size());
        assertEquals(BookingStatus.HELD, booking.getStatus());
        assertEquals(NOW.plusMinutes(10), booking.getHoldExpiresAt());
        assertEquals(NOW, booking.getCreatedAt());
        assertEquals(Money.vnd(RENTAL), booking.getTotal());
        assertEquals(2, booking.getPartySize());
        assertEquals(CUSTOMER_ID, booking.getCustomer().getId());
        assertEquals(10, booking.getCode().length());
        assertEquals(43, booking.getQrToken().length());
        assertEquals(1, booking.getStatusHistory().size());
        verify(customers).registerOrUpdate(CONTACT.toDomain());
    }

    // Viewpoint: VP7
    @Test
    public void test_hold_GuestWithoutContact_002() {
        // Khách vãng lai không nhập liên hệ.
        HoldBookingRequest request = new HoldBookingRequest(RESOURCE_ID, START, END, 2, null);
        log.input("request = {}", request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.hold(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CONTACT", thrown.code());
        verify(bookings, never()).save(any());
    }

    // Viewpoint: VP7
    @Test
    public void test_hold_NullRequest_003() {
        // Request null.
        HoldBookingRequest request = null;
        log.input("request = {}", request);

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.hold(request));

        log.output("exception = {}", thrown.getClass().getSimpleName());
    }

    // ---------- hold(request, actor) ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_hold_CustomerUsesAccountProfile_004() {
        // Khách đã đăng nhập: dùng hồ sơ tài khoản, không tạo khách mới.
        HoldBookingRequest request = new HoldBookingRequest(RESOURCE_ID, START, END, 2, null);
        log.input("request = {}, actor = {}", request, OWNER);

        Booking booking = service.hold(request, OWNER);

        log.output("customerId = {}", booking.getCustomer().getId());
        assertSame(customer, booking.getCustomer());
        verify(customers, never()).registerOrUpdate(any());
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_hold_ResourceNotFound_005() {
        // Vị trí không tồn tại.
        HoldBookingRequest request = new HoldBookingRequest(99L, START, END, 2, CONTACT);
        log.input("request = {}, actor = {}", request, Actor.GUEST);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.hold(request, Actor.GUEST));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_hold_NullResourceId_006() {
        // Mã vị trí null.
        HoldBookingRequest request = new HoldBookingRequest(null, START, END, 2, CONTACT);
        log.input("request = {}, actor = {}", request, Actor.GUEST);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.hold(request, Actor.GUEST));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_hold_ResourceWithoutZone_007() {
        // Vị trí chưa gán khu vực: kiểm tra khung giờ với chi nhánh rỗng.
        resource.setZone(null);
        HoldBookingRequest request = new HoldBookingRequest(RESOURCE_ID, START, END, 2, CONTACT);
        log.input("request = {}, actor = {}", request, Actor.GUEST);

        Booking booking = service.hold(request, Actor.GUEST);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.HELD, booking.getStatus());
        verify(windowPolicy).validate(START, END, null);
    }

    // Viewpoint: VP3
    @Test
    public void test_hold_InvalidWindow_008() {
        // Khung giờ bị BookingWindowPolicy từ chối: không lưu đơn.
        doThrow(new DomainException("OUTSIDE_OPENING_HOURS", "ngoài giờ")).when(windowPolicy).validate(START, END, branch);
        HoldBookingRequest request = new HoldBookingRequest(RESOURCE_ID, START, END, 2, CONTACT);
        log.input("request = {}, actor = {}", request, Actor.GUEST);

        DomainException thrown = assertThrows(DomainException.class, () -> service.hold(request, Actor.GUEST));

        log.output("code = {}", thrown.code());
        assertEquals("OUTSIDE_OPENING_HOURS", thrown.code());
        verify(bookings, never()).save(any());
    }

    // Viewpoint: VP7
    @Test
    public void test_hold_NullStartAt_009() {
        // Giờ bắt đầu null: chuyển nguyên cho BookingWindowPolicy kiểm tra.
        doThrow(new DomainException("INVALID_TIME_RANGE", "sai")).when(windowPolicy).validate(null, END, branch);
        HoldBookingRequest request = new HoldBookingRequest(RESOURCE_ID, null, END, 2, CONTACT);
        log.input("request = {}, actor = {}", request, Actor.GUEST);

        DomainException thrown = assertThrows(DomainException.class, () -> service.hold(request, Actor.GUEST));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_TIME_RANGE", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_hold_ResourceNotBookable_010() {
        // Vị trí không đặt được hoặc vượt sức chứa.
        doThrow(new DomainException("CAPACITY_EXCEEDED", "quá")).when(resourceService).requireBookable(resource, 2);
        HoldBookingRequest request = new HoldBookingRequest(RESOURCE_ID, START, END, 2, CONTACT);
        log.input("request = {}, actor = {}", request, Actor.GUEST);

        DomainException thrown = assertThrows(DomainException.class, () -> service.hold(request, Actor.GUEST));

        log.output("code = {}", thrown.code());
        assertEquals("CAPACITY_EXCEEDED", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_hold_SlotTaken_011() {
        // BR-01: khung giờ đã có người đặt.
        when(bookings.existsOccupying(RESOURCE_ID, new TimeRange(START, END), null)).thenReturn(true);
        HoldBookingRequest request = new HoldBookingRequest(RESOURCE_ID, START, END, 2, CONTACT);
        log.input("request = {}, actor = {}", request, Actor.GUEST);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.hold(request, Actor.GUEST));

        log.output("code = {}", thrown.code());
        assertEquals("SLOT_UNAVAILABLE", thrown.code());
        verify(bookings, never()).save(any());
    }

    // Viewpoint: VP3
    @Test
    public void test_hold_VoucherWithMembership_012() {
        // Q8: không dùng chung voucher với giờ thành viên.
        HoldBookingRequest request = request(TEXT_1_BYTE, true, List.of());
        log.input("request = {}, actor = {}", request, OWNER);

        DomainException thrown = assertThrows(DomainException.class, () -> service.hold(request, OWNER));

        log.output("code = {}", thrown.code());
        assertEquals("VOUCHER_MEMBERSHIP_CONFLICT", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_hold_MembershipAsGuest_013() {
        // Khách vãng lai không được dùng giờ thành viên.
        HoldBookingRequest request = request(null, true, List.of());
        log.input("request = {}, actor = {}", request, Actor.GUEST);

        DomainException thrown = assertThrows(DomainException.class, () -> service.hold(request, Actor.GUEST));

        log.output("code = {}", thrown.code());
        assertEquals("MEMBERSHIP_LOGIN_REQUIRED", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_hold_MembershipAsStaff_014() {
        // Nhân viên đặt hộ không được dùng giờ thành viên.
        HoldBookingRequest request = request(null, true, List.of());
        log.input("request = {}, actor = {}", request, STAFF);

        DomainException thrown = assertThrows(DomainException.class, () -> service.hold(request, STAFF));

        log.output("code = {}", thrown.code());
        assertEquals("MEMBERSHIP_LOGIN_REQUIRED", thrown.code());
    }

    // Viewpoint: VP2, VP3
    @Test
    public void test_hold_EmptyVoucherCode_015() {
        // Mã voucher rỗng coi như không dùng.
        HoldBookingRequest request = request("", false, List.of());
        log.input("request = {}, actor = {}", request, OWNER);

        Booking booking = service.hold(request, OWNER);

        log.output("total = {}", booking.getTotal());
        assertEquals(Money.vnd(RENTAL), booking.getTotal());
        verify(vouchers, never()).redeem(any(), any(), any());
    }

    // Viewpoint: VP2, VP3
    @Test
    public void test_hold_BlankVoucherCode_016() {
        // Mã voucher chỉ có khoảng trắng coi như không dùng.
        HoldBookingRequest request = request("   ", false, List.of());
        log.input("request = {}, actor = {}", request, OWNER);

        Booking booking = service.hold(request, OWNER);

        log.output("total = {}", booking.getTotal());
        assertEquals(Money.vnd(RENTAL), booking.getTotal());
        verify(vouchers, never()).redeem(any(), any(), any());
    }

    // Viewpoint: VP2, VP3
    @Test
    public void test_hold_Voucher1Byte_017() {
        // Mã voucher ký tự 1 byte: thêm dòng giảm trừ, vẫn còn phải trả.
        when(vouchers.redeem(eq(TEXT_1_BYTE), any(Booking.class), eq(NOW))).thenReturn(Money.vnd(50_000));
        HoldBookingRequest request = request(TEXT_1_BYTE, false, List.of());
        log.input("request = {}, actor = {}", request, OWNER);

        Booking booking = service.hold(request, OWNER);

        log.output("status = {}, discount = {}, total = {}", booking.getStatus(), booking.sumOf(ChargeType.VOUCHER_DISCOUNT), booking.getTotal());
        assertEquals(BookingStatus.HELD, booking.getStatus());
        assertEquals(Money.vnd(50_000), booking.sumOf(ChargeType.VOUCHER_DISCOUNT));
        assertEquals(Money.vnd(150_000), booking.getTotal());
    }

    // Viewpoint: VP2
    @Test
    public void test_hold_Voucher2Byte_018() {
        // Mã voucher ký tự 2 byte.
        when(vouchers.redeem(eq(TEXT_2_BYTE), any(Booking.class), eq(NOW))).thenReturn(Money.vnd(50_000));
        HoldBookingRequest request = request(TEXT_2_BYTE, false, List.of());
        log.input("request = {}, actor = {}", request, OWNER);

        Booking booking = service.hold(request, OWNER);

        log.output("discount = {}", booking.sumOf(ChargeType.VOUCHER_DISCOUNT));
        assertEquals(Money.vnd(50_000), booking.sumOf(ChargeType.VOUCHER_DISCOUNT));
    }

    // Viewpoint: VP2
    @Test
    public void test_hold_Voucher3Byte_019() {
        // Mã voucher ký tự 3 byte.
        when(vouchers.redeem(eq(TEXT_3_BYTE), any(Booking.class), eq(NOW))).thenReturn(Money.vnd(50_000));
        HoldBookingRequest request = request(TEXT_3_BYTE, false, List.of());
        log.input("request = {}, actor = {}", request, OWNER);

        Booking booking = service.hold(request, OWNER);

        log.output("discount = {}", booking.sumOf(ChargeType.VOUCHER_DISCOUNT));
        assertEquals(Money.vnd(50_000), booking.sumOf(ChargeType.VOUCHER_DISCOUNT));
    }

    // Viewpoint: VP3
    @Test
    public void test_hold_VoucherZeroDiscount_020() {
        // Cận dưới của addDeduction: giảm 0 đồng thì không thêm dòng.
        when(vouchers.redeem(eq(TEXT_1_BYTE), any(Booking.class), eq(NOW))).thenReturn(Money.ZERO);
        HoldBookingRequest request = request(TEXT_1_BYTE, false, List.of());
        log.input("request = {}, actor = {}", request, OWNER);

        Booking booking = service.hold(request, OWNER);

        log.output("chargeCount = {}", booking.getCharges().size());
        assertEquals(1, booking.getCharges().size());
    }

    // Viewpoint: VP3
    @Test
    public void test_hold_VoucherFullDiscount_021() {
        // Voucher phủ hết: xác nhận ngay, không qua cổng thanh toán.
        when(vouchers.redeem(eq(TEXT_1_BYTE), any(Booking.class), eq(NOW))).thenReturn(Money.vnd(RENTAL));
        HoldBookingRequest request = request(TEXT_1_BYTE, false, List.of());
        log.input("request = {}, actor = {}", request, OWNER);

        Booking booking = service.hold(request, OWNER);

        log.output("status = {}, total = {}", booking.getStatus(), booking.getTotal());
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals(Money.ZERO, booking.getTotal());
        verify(vouchers).markUsed(booking);
        verify(memberships).markUsed(booking);
        verify(notifications).bookingConfirmed(booking);
    }

    // Viewpoint: VP3
    @Test
    public void test_hold_MembershipFullDeduction_022() {
        // BR-13: membership phủ hết: xác nhận ngay.
        when(memberships.deduct(any(Booking.class), any(PriceQuote.class), eq(NOW))).thenReturn(Money.vnd(RENTAL));
        HoldBookingRequest request = request(null, true, List.of());
        log.input("request = {}, actor = {}", request, OWNER);

        Booking booking = service.hold(request, OWNER);

        log.output("status = {}, deduction = {}", booking.getStatus(), booking.sumOf(ChargeType.MEMBERSHIP_DEDUCTION));
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals(Money.vnd(RENTAL), booking.sumOf(ChargeType.MEMBERSHIP_DEDUCTION));
        verify(notifications).bookingConfirmed(booking);
    }

    // Viewpoint: VP3
    @Test
    public void test_hold_MembershipPartialDeduction_023() {
        // Khoảng giữa: membership phủ một phần: vẫn giữ chỗ chờ thanh toán.
        when(memberships.deduct(any(Booking.class), any(PriceQuote.class), eq(NOW))).thenReturn(Money.vnd(50_000));
        HoldBookingRequest request = request(null, true, List.of());
        log.input("request = {}, actor = {}", request, OWNER);

        Booking booking = service.hold(request, OWNER);

        log.output("status = {}, total = {}", booking.getStatus(), booking.getTotal());
        assertEquals(BookingStatus.HELD, booking.getStatus());
        assertEquals(Money.vnd(150_000), booking.getTotal());
        verify(notifications, never()).bookingConfirmed(any());
    }

    // Viewpoint: VP3
    @Test
    public void test_hold_MembershipZeroDeduction_024() {
        // Cận dưới: không còn phút để trừ: không thêm dòng giảm trừ.
        when(memberships.deduct(any(Booking.class), any(PriceQuote.class), eq(NOW))).thenReturn(Money.ZERO);
        HoldBookingRequest request = request(null, true, List.of());
        log.input("request = {}, actor = {}", request, OWNER);

        Booking booking = service.hold(request, OWNER);

        log.output("status = {}, chargeCount = {}", booking.getStatus(), booking.getCharges().size());
        assertEquals(BookingStatus.HELD, booking.getStatus());
        assertEquals(1, booking.getCharges().size());
    }

    // Viewpoint: VP3
    @Test
    public void test_hold_TotalOneDong_025() {
        // Cận của isPositive: còn đúng 1 đồng thì vẫn phải thanh toán.
        when(memberships.deduct(any(Booking.class), any(PriceQuote.class), eq(NOW))).thenReturn(Money.vnd(RENTAL - 1));
        HoldBookingRequest request = request(null, true, List.of());
        log.input("request = {}, actor = {}", request, OWNER);

        Booking booking = service.hold(request, OWNER);

        log.output("status = {}, total = {}", booking.getStatus(), booking.getTotal());
        assertEquals(BookingStatus.HELD, booking.getStatus());
        assertEquals(Money.vnd(1), booking.getTotal());
    }

    // Viewpoint: VP4
    @Test
    public void test_hold_ZeroAddons_026() {
        // Vòng lặp add-on: 0 record.
        HoldBookingRequest request = request(null, false, List.of());
        log.input("request = {}, actor = {}", request, OWNER);

        Booking booking = service.hold(request, OWNER);

        log.output("addonCount = {}, chargeCount = {}", booking.getAddons().size(), booking.getCharges().size());
        assertEquals(0, booking.getAddons().size());
        assertEquals(1, booking.getCharges().size());
    }

    // Viewpoint: VP4
    @Test
    public void test_hold_OneAddon_027() {
        // Vòng lặp add-on: 1 record.
        List<AddonSelection> selections = List.of(new AddonSelection(1L, 2));
        when(addons.reserve(eq(branch), any(TimeRange.class), eq(2), eq(selections))).thenReturn(List.of(addonLine(40_000)));
        HoldBookingRequest request = request(null, false, selections);
        log.input("request = {}, actor = {}", request, OWNER);

        Booking booking = service.hold(request, OWNER);

        log.output("addonCount = {}, addonCharge = {}, total = {}", booking.getAddons().size(), booking.sumOf(ChargeType.ADDON), booking.getTotal());
        assertEquals(1, booking.getAddons().size());
        assertEquals(Money.vnd(40_000), booking.sumOf(ChargeType.ADDON));
        assertEquals(Money.vnd(240_000), booking.getTotal());
    }

    // Viewpoint: VP4
    @Test
    public void test_hold_ManyAddons_028() {
        // Vòng lặp add-on: nhiều hơn 1 record.
        List<AddonSelection> selections = List.of(new AddonSelection(1L, 2), new AddonSelection(2L, 1));
        when(addons.reserve(eq(branch), any(TimeRange.class), eq(2), eq(selections)))
                .thenReturn(List.of(addonLine(40_000), addonLine(10_000)));
        HoldBookingRequest request = request(null, false, selections);
        log.input("request = {}, actor = {}", request, OWNER);

        Booking booking = service.hold(request, OWNER);

        log.output("addonCount = {}, addonCharge = {}, total = {}", booking.getAddons().size(), booking.sumOf(ChargeType.ADDON), booking.getTotal());
        assertEquals(2, booking.getAddons().size());
        assertEquals(Money.vnd(50_000), booking.sumOf(ChargeType.ADDON));
        assertEquals(Money.vnd(250_000), booking.getTotal());
    }

    // Viewpoint: VP1
    @Test
    public void test_hold_PartySizeMaxValue_029() {
        // Số người lớn nhất của kiểu int được chuyển nguyên cho kiểm tra sức chứa.
        HoldBookingRequest request = new HoldBookingRequest(RESOURCE_ID, START, END, Integer.MAX_VALUE, CONTACT);
        log.input("request = {}, actor = {}", request, Actor.GUEST);

        Booking booking = service.hold(request, Actor.GUEST);

        log.output("partySize = {}", booking.getPartySize());
        assertEquals(Integer.MAX_VALUE, booking.getPartySize());
        verify(resourceService).requireBookable(resource, Integer.MAX_VALUE);
    }

    // Viewpoint: VP1
    @Test
    public void test_hold_PartySizeMinValue_030() {
        // Số người nhỏ nhất của kiểu int được chuyển nguyên cho kiểm tra sức chứa.
        HoldBookingRequest request = new HoldBookingRequest(RESOURCE_ID, START, END, Integer.MIN_VALUE, CONTACT);
        log.input("request = {}, actor = {}", request, Actor.GUEST);

        Booking booking = service.hold(request, Actor.GUEST);

        log.output("partySize = {}", booking.getPartySize());
        assertEquals(Integer.MIN_VALUE, booking.getPartySize());
        verify(resourceService).requireBookable(resource, Integer.MIN_VALUE);
    }

    // Viewpoint: VP7
    @Test
    public void test_hold_NullActor_031() {
        // Actor null.
        HoldBookingRequest request = new HoldBookingRequest(RESOURCE_ID, START, END, 2, CONTACT);
        Actor actor = null;
        log.input("request = {}, actor = {}", request, actor);

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.hold(request, actor));

        log.output("exception = {}", thrown.getClass().getSimpleName());
    }

    // Viewpoint: VP7
    @Test
    public void test_hold_NullRequestWithActor_032() {
        // Request null khi có actor.
        HoldBookingRequest request = null;
        log.input("request = {}, actor = {}", request, OWNER);

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.hold(request, OWNER));

        log.output("exception = {}", thrown.getClass().getSimpleName());
    }

    // ---------- onPaymentSucceeded ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_onPaymentSucceeded_HeldExactAmount_033() {
        // Đơn HELD, trả đúng số tiền: xác nhận, ghi lịch sử, đánh dấu đã dùng ưu đãi.
        Booking booking = bookingIn(BookingStatus.HELD);
        Money paid = Money.vnd(RENTAL);
        log.input("bookingId = {}, paid = {}", BOOKING_ID, paid);

        service.onPaymentSucceeded(BOOKING_ID, paid);

        log.output("status = {}, paidAmount = {}, historySize = {}", booking.getStatus(), booking.getPaidAmount(), booking.getStatusHistory().size());
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals(RENTAL, booking.getPaidAmount());
        assertEquals(1, booking.getStatusHistory().size());
        verify(vouchers).markUsed(booking);
        verify(memberships).markUsed(booking);
        verify(notifications).bookingConfirmed(booking);
    }

    // Viewpoint: VP3
    @Test
    public void test_onPaymentSucceeded_AmountOneBelow_034() {
        // Cận dưới: thiếu 1 đồng.
        Booking booking = bookingIn(BookingStatus.HELD);
        Money paid = Money.vnd(RENTAL - 1);
        log.input("bookingId = {}, paid = {}", BOOKING_ID, paid);

        DomainException thrown = assertThrows(DomainException.class, () -> service.onPaymentSucceeded(BOOKING_ID, paid));

        log.output("code = {}, status = {}", thrown.code(), booking.getStatus());
        assertEquals("AMOUNT_MISMATCH", thrown.code());
        assertEquals(BookingStatus.HELD, booking.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_onPaymentSucceeded_AmountOneAbove_035() {
        // Cận trên: thừa 1 đồng.
        Booking booking = bookingIn(BookingStatus.HELD);
        Money paid = Money.vnd(RENTAL + 1);
        log.input("bookingId = {}, paid = {}", BOOKING_ID, paid);

        DomainException thrown = assertThrows(DomainException.class, () -> service.onPaymentSucceeded(BOOKING_ID, paid));

        log.output("code = {}, status = {}", thrown.code(), booking.getStatus());
        assertEquals("AMOUNT_MISMATCH", thrown.code());
        assertEquals(BookingStatus.HELD, booking.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_onPaymentSucceeded_ExpiredSlotFree_036() {
        // BR-11: webhook về trễ, đơn EXPIRED, chỗ còn trống: xác nhận lại.
        Booking booking = bookingIn(BookingStatus.EXPIRED);
        Money paid = Money.vnd(RENTAL);
        log.input("bookingId = {}, paid = {}", BOOKING_ID, paid);

        service.onPaymentSucceeded(BOOKING_ID, paid);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        verify(resources).findByIdForUpdate(RESOURCE_ID);
        verify(notifications).bookingConfirmed(booking);
    }

    // Viewpoint: VP3
    @Test
    public void test_onPaymentSucceeded_FailedSlotFree_037() {
        // Webhook về trễ, đơn FAILED, chỗ còn trống: xác nhận lại.
        Booking booking = bookingIn(BookingStatus.FAILED);
        Money paid = Money.vnd(RENTAL);
        log.input("bookingId = {}, paid = {}", BOOKING_ID, paid);

        service.onPaymentSucceeded(BOOKING_ID, paid);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_onPaymentSucceeded_ExpiredSlotTaken_038() {
        // Chỗ đã bị người khác đặt: chờ hoàn tiền.
        Booking booking = bookingIn(BookingStatus.EXPIRED);
        when(bookings.existsOccupying(RESOURCE_ID, booking.getPeriod(), BOOKING_ID)).thenReturn(true);
        Money paid = Money.vnd(RENTAL);
        log.input("bookingId = {}, paid = {}", BOOKING_ID, paid);

        service.onPaymentSucceeded(BOOKING_ID, paid);

        log.output("status = {}, paidAmount = {}", booking.getStatus(), booking.getPaidAmount());
        assertEquals(BookingStatus.REFUND_PENDING, booking.getStatus());
        assertEquals(RENTAL, booking.getPaidAmount());
        verify(notifications).refundAfterLatePayment(booking, paid);
        verify(notifications, never()).bookingConfirmed(any());
    }

    // Viewpoint: VP3
    @Test
    public void test_onPaymentSucceeded_FailedSlotTaken_039() {
        // Đơn FAILED, chỗ đã bị đặt: chờ hoàn tiền.
        Booking booking = bookingIn(BookingStatus.FAILED);
        when(bookings.existsOccupying(RESOURCE_ID, booking.getPeriod(), BOOKING_ID)).thenReturn(true);
        Money paid = Money.vnd(RENTAL);
        log.input("bookingId = {}, paid = {}", BOOKING_ID, paid);

        service.onPaymentSucceeded(BOOKING_ID, paid);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.REFUND_PENDING, booking.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_onPaymentSucceeded_ExpiredWithDeductions_040() {
        // Đơn có voucher: lượt đã trả lại nên không kích hoạt lại, dù chỗ còn trống.
        Booking booking = bookingIn(BookingStatus.EXPIRED);
        addCharge(booking, ChargeType.VOUCHER_DISCOUNT, 50_000);
        Money paid = Money.vnd(150_000);
        log.input("bookingId = {}, paid = {}", BOOKING_ID, paid);

        service.onPaymentSucceeded(BOOKING_ID, paid);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.REFUND_PENDING, booking.getStatus());
        verify(notifications).refundAfterLatePayment(booking, paid);
    }

    // Viewpoint: VP3
    @Test
    public void test_onPaymentSucceeded_ExpiredAmountMismatch_041() {
        // Webhook về trễ, chỗ còn trống nhưng sai số tiền.
        Booking booking = bookingIn(BookingStatus.EXPIRED);
        Money paid = Money.vnd(RENTAL - 1);
        log.input("bookingId = {}, paid = {}", BOOKING_ID, paid);

        DomainException thrown = assertThrows(DomainException.class, () -> service.onPaymentSucceeded(BOOKING_ID, paid));

        log.output("code = {}, status = {}", thrown.code(), booking.getStatus());
        assertEquals("AMOUNT_MISMATCH", thrown.code());
        assertEquals(BookingStatus.EXPIRED, booking.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_onPaymentSucceeded_AlreadyConfirmed_042() {
        // Đơn đã xác nhận, không có yêu cầu đổi chỗ đang chờ mà vẫn nhận thêm tiền (yêu cầu đổi đã hết hạn): hoàn số tiền đó.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        Money paid = Money.vnd(30_000);
        log.input("bookingId = {}, paid = {}", BOOKING_ID, paid);

        service.onPaymentSucceeded(BOOKING_ID, paid);

        log.output("status = {}, paidAmount = {}, historySize = {}", booking.getStatus(), booking.getPaidAmount(),
                booking.getStatusHistory().size());
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals(RENTAL, booking.getPaidAmount());
        assertEquals(0, booking.getStatusHistory().size());
        verify(refunds).request(booking, paid, "UNEXPECTED_PAYMENT", NOW);
        verify(notifications).refundAfterLatePayment(booking, paid);
    }

    // Viewpoint: VP3
    @Test
    public void test_onPaymentSucceeded_Cancelled_043() {
        // Đơn đã hủy: bỏ qua.
        Booking booking = bookingIn(BookingStatus.CANCELLED);
        Money paid = Money.vnd(RENTAL);
        log.input("bookingId = {}, paid = {}", BOOKING_ID, paid);

        service.onPaymentSucceeded(BOOKING_ID, paid);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        verify(notifications, never()).bookingConfirmed(any());
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_onPaymentSucceeded_BookingNotFound_044() {
        // Đơn không tồn tại.
        Money paid = Money.vnd(RENTAL);
        log.input("bookingId = {}, paid = {}", BOOKING_ID, paid);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.onPaymentSucceeded(BOOKING_ID, paid));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_onPaymentSucceeded_NullBookingId_045() {
        // Mã đơn null.
        Long bookingId = null;
        Money paid = Money.vnd(RENTAL);
        log.input("bookingId = {}, paid = {}", bookingId, paid);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.onPaymentSucceeded(bookingId, paid));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_onPaymentSucceeded_NullPaid_046() {
        // Số tiền null.
        bookingIn(BookingStatus.HELD);
        Money paid = null;
        log.input("bookingId = {}, paid = {}", BOOKING_ID, paid);

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.onPaymentSucceeded(BOOKING_ID, paid));

        log.output("exception = {}", thrown.getClass().getSimpleName());
    }

    // ---------- onPaymentFailed ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_onPaymentFailed_Held_047() {
        // FR-PAY-05: đơn HELD: chuyển FAILED, trả lại ưu đãi, báo khách.
        Booking booking = bookingIn(BookingStatus.HELD);
        log.input("bookingId = {}", BOOKING_ID);

        service.onPaymentFailed(BOOKING_ID);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.FAILED, booking.getStatus());
        verify(vouchers).release(booking);
        verify(memberships).release(booking);
        verify(notifications).paymentFailed(booking);
    }

    // Viewpoint: VP3
    @Test
    public void test_onPaymentFailed_NotHeld_048() {
        // Đơn không ở HELD: bỏ qua.
        Booking booking = bookingIn(BookingStatus.CONFIRMED);
        log.input("bookingId = {}", BOOKING_ID);

        service.onPaymentFailed(BOOKING_ID);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        verify(vouchers, never()).release(any());
        verify(bookings, never()).save(any());
    }

    // Viewpoint: VP7
    @Test
    public void test_onPaymentFailed_BookingNotFound_049() {
        // Đơn không tồn tại.
        log.input("bookingId = {}", BOOKING_ID);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.onPaymentFailed(BOOKING_ID));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_onPaymentFailed_NullBookingId_050() {
        // Mã đơn null.
        Long bookingId = null;
        log.input("bookingId = {}", bookingId);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.onPaymentFailed(bookingId));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // ---------- cancel ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_cancel_OwnerBeforeDeadline_051() {
        // UC06: chủ đơn hủy sớm: hoàn 100%, trả lại ưu đãi, báo khách.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        log.input("bookingId = {}, actor = {}", BOOKING_ID, OWNER);

        Money refund = service.cancel(BOOKING_ID, OWNER);

        log.output("refund = {}, status = {}", refund, booking.getStatus());
        assertEquals(Money.vnd(RENTAL), refund);
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        verify(vouchers).release(booking);
        verify(memberships).release(booking);
        verify(refunds).request(booking, Money.vnd(RENTAL), "CUSTOMER_CANCELLED", NOW);
        verify(notifications).bookingCancelled(booking, Money.vnd(RENTAL));
    }

    // Viewpoint: VP3
    @Test
    public void test_cancel_StaffCancelsAnyBooking_052() {
        // Nhân viên hủy được đơn của khách.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        log.input("bookingId = {}, actor = {}", BOOKING_ID, STAFF);

        service.cancel(BOOKING_ID, STAFF);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_cancel_ExactDeadline_053() {
        // Cận trên: đúng mốc 2 giờ trước giờ bắt đầu vẫn được hủy.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        clock.set(CHANGE_DEADLINE);
        log.input("bookingId = {}, actor = {}", BOOKING_ID, OWNER);

        service.cancel(BOOKING_ID, OWNER);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_cancel_OneSecondAfterDeadline_054() {
        // Quá mốc 1 giây: từ chối.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        clock.set(CHANGE_DEADLINE.plusSeconds(1));
        log.input("bookingId = {}, actor = {}", BOOKING_ID, OWNER);

        DomainException thrown = assertThrows(DomainException.class, () -> service.cancel(BOOKING_ID, OWNER));

        log.output("code = {}, status = {}", thrown.code(), booking.getStatus());
        assertEquals("CHANGE_DEADLINE_PASSED", thrown.code());
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_cancel_NotConfirmed_055() {
        // Đơn chưa thanh toán không hủy theo luồng này.
        bookingIn(BookingStatus.HELD);
        log.input("bookingId = {}, actor = {}", BOOKING_ID, OWNER);

        DomainException thrown = assertThrows(DomainException.class, () -> service.cancel(BOOKING_ID, OWNER));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_BOOKING_STATE", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_cancel_OtherCustomer_056() {
        // Đơn của người khác trả 404 như không tồn tại.
        paidBookingIn(BookingStatus.CONFIRMED);
        log.input("bookingId = {}, actor = {}", BOOKING_ID, OTHER_CUSTOMER);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.cancel(BOOKING_ID, OTHER_CUSTOMER));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_cancel_Guest_057() {
        // Khách vãng lai không hủy được đơn.
        paidBookingIn(BookingStatus.CONFIRMED);
        log.input("bookingId = {}, actor = {}", BOOKING_ID, Actor.GUEST);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.cancel(BOOKING_ID, Actor.GUEST));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_cancel_NullActor_058() {
        // Actor null.
        paidBookingIn(BookingStatus.CONFIRMED);
        Actor actor = null;
        log.input("bookingId = {}, actor = {}", BOOKING_ID, actor);

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.cancel(BOOKING_ID, actor));

        log.output("exception = {}", thrown.getClass().getSimpleName());
    }

    // Viewpoint: VP7
    @Test
    public void test_cancel_BookingNotFound_059() {
        // Đơn không tồn tại.
        log.input("bookingId = {}, actor = {}", BOOKING_ID, OWNER);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.cancel(BOOKING_ID, OWNER));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_cancel_PaidZero_060() {
        // Đơn 0 đồng (membership phủ hết): số tiền hoàn bằng 0.
        Booking booking = bookingIn(BookingStatus.CONFIRMED);
        log.input("bookingId = {}, actor = {}", BOOKING_ID, OWNER);

        Money refund = service.cancel(BOOKING_ID, OWNER);

        log.output("refund = {}", refund);
        assertEquals(Money.ZERO, refund);
        verify(refunds).request(booking, Money.ZERO, "CUSTOMER_CANCELLED", NOW);
    }

    // ---------- reschedule ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_reschedule_CheaperSlot_061() {
        // Khung mới rẻ hơn: dời khung giờ, hoàn phần chênh lệch.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        when(pricing.quote(any(), any())).thenReturn(quote(150_000));
        log.input("bookingId = {}, request = {}, actor = {}", BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        RescheduleResult result = service.reschedule(BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        log.output("refund = {}, paidAmount = {}, period = {}, rental = {}",
                result.refund(), booking.getPaidAmount(), booking.getPeriod(), booking.sumOf(ChargeType.RENTAL));
        assertEquals(Money.vnd(50_000), result.refund());
        assertEquals(150_000L, booking.getPaidAmount());
        assertEquals(new TimeRange(NEW_START, NEW_END), booking.getPeriod());
        assertEquals(Money.vnd(150_000), booking.sumOf(ChargeType.RENTAL));
        verify(refunds).request(booking, Money.vnd(50_000), "RESCHEDULE_PRICE_DIFF", NOW);
        verify(notifications).bookingRescheduled(booking, Money.vnd(50_000));
    }

    // Viewpoint: VP3
    @Test
    public void test_reschedule_SamePrice_062() {
        // Cận: giá bằng số đã trả: không hoàn tiền.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        log.input("bookingId = {}, request = {}, actor = {}", BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        RescheduleResult result = service.reschedule(BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        log.output("refund = {}, paidAmount = {}", result.refund(), booking.getPaidAmount());
        assertEquals(Money.ZERO, result.refund());
        assertEquals(RENTAL, booking.getPaidAmount());
        verify(refunds, never()).request(any(), any(), any(), any());
    }

    // Viewpoint: VP3
    @Test
    public void test_reschedule_PriceOneDongHigher_063() {
        // Cận trên: đắt hơn số đã trả 1 đồng: giữ chỗ mới 10 phút chờ trả 1 đồng, đơn chưa đổi.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        when(pricing.quote(any(), any())).thenReturn(quote(RENTAL + 1));
        log.input("bookingId = {}, request = {}, actor = {}", BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        RescheduleResult result = service.reschedule(BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        log.output("paymentRequired = {}, amountDue = {}, pendingPeriod = {}, pendingExpiresAt = {}, period = {}, rental = {}",
                result.paymentRequired(), result.amountDue(), booking.getPendingPeriod(), booking.getPendingExpiresAt(),
                booking.getPeriod(), booking.sumOf(ChargeType.RENTAL));
        assertTrue(result.paymentRequired());
        assertEquals(Money.vnd(1), result.amountDue());
        assertEquals(new TimeRange(NEW_START, NEW_END), booking.getPendingPeriod());
        assertEquals(NOW.plusMinutes(10), booking.getPendingExpiresAt());
        assertEquals(new TimeRange(START, END), booking.getPeriod());
        assertEquals(Money.vnd(RENTAL), booking.sumOf(ChargeType.RENTAL));
        verify(notifications, never()).bookingRescheduled(any(), any());
        verify(vouchers, never()).applyChange(any(), any());
    }

    // Viewpoint: VP3
    @Test
    public void test_reschedule_SameSlot_064() {
        // Khung giờ mới trùng khung hiện tại.
        paidBookingIn(BookingStatus.CONFIRMED);
        log.input("bookingId = {}, request = {}, actor = {}", BOOKING_ID, change(START, END), OWNER);

        DomainException thrown = assertThrows(DomainException.class, () -> service.reschedule(BOOKING_ID, change(START, END), OWNER));

        log.output("code = {}", thrown.code());
        assertEquals("RESCHEDULE_SAME_SLOT", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_reschedule_DifferentDuration_065() {
        // Được đổi thời lượng: 2 giờ → 1 giờ, tiền thuê tính lại theo khung mới, hoàn phần chênh.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        LocalDateTime newEnd = NEW_START.plusHours(1);
        when(pricing.quote(any(), any())).thenReturn(new PriceQuote(2, 30, Money.vnd(100_000)));
        log.input("bookingId = {}, request = {}, actor = {}", BOOKING_ID, change(NEW_START, newEnd), OWNER);

        RescheduleResult result = service.reschedule(BOOKING_ID, change(NEW_START, newEnd), OWNER);

        log.output("period = {}, rental = {}, refund = {}", booking.getPeriod(), booking.sumOf(ChargeType.RENTAL), result.refund());
        assertEquals(new TimeRange(NEW_START, newEnd), booking.getPeriod());
        assertEquals(Money.vnd(100_000), booking.sumOf(ChargeType.RENTAL));
        assertEquals(Money.vnd(100_000), result.refund());
        verify(memberships).applyChange(booking, new PriceQuote(2, 30, Money.vnd(100_000)));
    }

    // Viewpoint: VP3
    @Test
    public void test_reschedule_ResourceNotBookable_066() {
        // Vị trí không còn đặt được.
        paidBookingIn(BookingStatus.CONFIRMED);
        doThrow(new DomainException("RESOURCE_NOT_BOOKABLE", "khóa")).when(resourceService).requireBookable(resource, 2);
        log.input("bookingId = {}, request = {}, actor = {}", BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        DomainException thrown = assertThrows(DomainException.class, () -> service.reschedule(BOOKING_ID, change(NEW_START, NEW_END), OWNER));

        log.output("code = {}", thrown.code());
        assertEquals("RESOURCE_NOT_BOOKABLE", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_reschedule_SlotTaken_067() {
        // Khung mới đã có người đặt.
        paidBookingIn(BookingStatus.CONFIRMED);
        when(bookings.existsOccupying(RESOURCE_ID, new TimeRange(NEW_START, NEW_END), BOOKING_ID)).thenReturn(true);
        log.input("bookingId = {}, request = {}, actor = {}", BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.reschedule(BOOKING_ID, change(NEW_START, NEW_END), OWNER));

        log.output("code = {}", thrown.code());
        assertEquals("SLOT_UNAVAILABLE", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_reschedule_AddonUnavailable_068() {
        // Add-on hết hàng ở khung mới.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        doThrow(new ConflictException("ADDON_OUT_OF_STOCK", "hết")).when(addons).recheck(booking, branch, new TimeRange(NEW_START, NEW_END));
        log.input("bookingId = {}, request = {}, actor = {}", BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.reschedule(BOOKING_ID, change(NEW_START, NEW_END), OWNER));

        log.output("code = {}", thrown.code());
        assertEquals("ADDON_OUT_OF_STOCK", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_reschedule_ExactDeadline_069() {
        // Cận trên: đúng mốc 2 giờ vẫn được đổi.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        clock.set(CHANGE_DEADLINE);
        log.input("bookingId = {}, request = {}, actor = {}", BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        service.reschedule(BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        log.output("period = {}", booking.getPeriod());
        assertEquals(new TimeRange(NEW_START, NEW_END), booking.getPeriod());
    }

    // Viewpoint: VP3
    @Test
    public void test_reschedule_OneSecondAfterDeadline_070() {
        // Quá mốc 1 giây: từ chối.
        paidBookingIn(BookingStatus.CONFIRMED);
        clock.set(CHANGE_DEADLINE.plusSeconds(1));
        log.input("bookingId = {}, request = {}, actor = {}", BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        DomainException thrown = assertThrows(DomainException.class, () -> service.reschedule(BOOKING_ID, change(NEW_START, NEW_END), OWNER));

        log.output("code = {}", thrown.code());
        assertEquals("CHANGE_DEADLINE_PASSED", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_reschedule_NotConfirmed_071() {
        // Đơn không ở CONFIRMED.
        bookingIn(BookingStatus.CHECKED_IN);
        log.input("bookingId = {}, request = {}, actor = {}", BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        DomainException thrown = assertThrows(DomainException.class, () -> service.reschedule(BOOKING_ID, change(NEW_START, NEW_END), OWNER));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_BOOKING_STATE", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_reschedule_OtherCustomer_072() {
        // Đơn của người khác.
        paidBookingIn(BookingStatus.CONFIRMED);
        log.input("bookingId = {}, request = {}, actor = {}", BOOKING_ID, change(NEW_START, NEW_END), OTHER_CUSTOMER);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.reschedule(BOOKING_ID, change(NEW_START, NEW_END), OTHER_CUSTOMER));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_reschedule_NullActor_073() {
        // Actor null.
        paidBookingIn(BookingStatus.CONFIRMED);
        Actor actor = null;
        log.input("bookingId = {}, request = {}, actor = {}", BOOKING_ID, change(NEW_START, NEW_END), actor);

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.reschedule(BOOKING_ID, change(NEW_START, NEW_END), actor));

        log.output("exception = {}", thrown.getClass().getSimpleName());
    }

    // Viewpoint: VP7
    @Test
    public void test_reschedule_NullNewStart_074() {
        // Giờ mới null: chuyển nguyên cho BookingWindowPolicy kiểm tra.
        paidBookingIn(BookingStatus.CONFIRMED);
        doThrow(new DomainException("INVALID_TIME_RANGE", "sai")).when(windowPolicy).validate(null, NEW_END, branch);
        LocalDateTime newStart = null;
        log.input("bookingId = {}, request = {}, actor = {}", BOOKING_ID, change(newStart, NEW_END), OWNER);

        DomainException thrown = assertThrows(DomainException.class, () -> service.reschedule(BOOKING_ID, change(newStart, NEW_END), OWNER));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_TIME_RANGE", thrown.code());
    }

    // Viewpoint: VP4
    @Test
    public void test_reschedule_ZeroCharges_075() {
        // Vòng lặp khoản thu: 0 record, chỉ dời khung giờ.
        Booking booking = bookingIn(BookingStatus.CONFIRMED);
        booking.getCharges().clear();
        when(pricing.quote(any(), any())).thenReturn(quote(0));
        log.input("bookingId = {}, request = {}, actor = {}", BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        RescheduleResult result = service.reschedule(BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        log.output("refund = {}, period = {}", result.refund(), booking.getPeriod());
        assertEquals(Money.ZERO, result.refund());
        assertEquals(new TimeRange(NEW_START, NEW_END), booking.getPeriod());
    }

    // Viewpoint: VP4
    @Test
    public void test_reschedule_OneCharge_076() {
        // Vòng lặp khoản thu: 1 record, dòng được dời sang khung mới.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        log.input("bookingId = {}, request = {}, actor = {}", BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        service.reschedule(BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        BookingCharge charge = booking.getCharges().get(0);
        log.output("startAt = {}, endAt = {}", charge.getStartAt(), charge.getEndAt());
        assertEquals(NEW_START, charge.getStartAt());
        assertEquals(NEW_END, charge.getEndAt());
    }

    // Viewpoint: VP3, VP4
    @Test
    public void test_reschedule_ManyChargeTypes_077() {
        // Vòng lặp nhiều loại khoản thu: thuê, voucher, membership đổi tiền; add-on = 12.500 × 2 block × 2 cái (không đổi số block).
        Booking booking = bookingIn(BookingStatus.CONFIRMED);
        booking.getAddons().add(new BookingAddon(booking, new Addon(branch, "A1", "Màn hình", 5, 12_500), 2, 12_500));
        addCharge(booking, ChargeType.ADDON, 50_000);
        addCharge(booking, ChargeType.VOUCHER_DISCOUNT, 20_000);
        addCharge(booking, ChargeType.MEMBERSHIP_DEDUCTION, 10_000);
        booking.setPaidAmount(220_000);
        when(pricing.quote(any(), any())).thenReturn(quote(180_000));
        when(vouchers.quoteChange(any(), any(), any(), any())).thenReturn(Money.vnd(15_000));
        when(memberships.quoteChange(any(), any(), any(), any())).thenReturn(Money.vnd(5_000));
        log.input("bookingId = {}, request = {}, actor = {}", BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        RescheduleResult result = service.reschedule(BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        log.output("rental = {}, addon = {}, voucher = {}, membership = {}, refund = {}, paidAmount = {}",
                booking.sumOf(ChargeType.RENTAL), booking.sumOf(ChargeType.ADDON), booking.sumOf(ChargeType.VOUCHER_DISCOUNT),
                booking.sumOf(ChargeType.MEMBERSHIP_DEDUCTION), result.refund(), booking.getPaidAmount());
        assertEquals(Money.vnd(180_000), booking.sumOf(ChargeType.RENTAL));
        assertEquals(Money.vnd(50_000), booking.sumOf(ChargeType.ADDON));
        assertEquals(Money.vnd(15_000), booking.sumOf(ChargeType.VOUCHER_DISCOUNT));
        assertEquals(Money.vnd(5_000), booking.sumOf(ChargeType.MEMBERSHIP_DEDUCTION));
        assertEquals(Money.vnd(10_000), result.refund());
        assertEquals(210_000L, booking.getPaidAmount());
    }

    // Viewpoint: VP3
    @Test
    public void test_reschedule_DeductionsEqualSubtotal_078() {
        // Cận: giảm trừ đúng bằng tạm tính: tổng mới 0, hoàn toàn bộ.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        when(pricing.quote(any(), any())).thenReturn(quote(150_000));
        when(vouchers.quoteChange(any(), any(), any(), any())).thenReturn(Money.vnd(150_000));
        log.input("bookingId = {}, request = {}, actor = {}", BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        RescheduleResult result = service.reschedule(BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        log.output("refund = {}, paidAmount = {}", result.refund(), booking.getPaidAmount());
        assertEquals(Money.vnd(RENTAL), result.refund());
        assertEquals(0L, booking.getPaidAmount());
    }

    // Viewpoint: VP3
    @Test
    public void test_reschedule_DeductionsExceedSubtotal_079() {
        // Giảm trừ vượt tạm tính: tổng mới không âm.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        when(pricing.quote(any(), any())).thenReturn(quote(150_000));
        when(vouchers.quoteChange(any(), any(), any(), any())).thenReturn(Money.vnd(300_000));
        log.input("bookingId = {}, request = {}, actor = {}", BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        RescheduleResult result = service.reschedule(BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        log.output("refund = {}, paidAmount = {}", result.refund(), booking.getPaidAmount());
        assertEquals(Money.vnd(RENTAL), result.refund());
        assertEquals(0L, booking.getPaidAmount());
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_reschedule_PendingPaymentNotExpired_170() {
        // Đang có yêu cầu đổi chỗ chờ thanh toán, còn 1 giây: từ chối yêu cầu mới.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        setPending(booking, 30_000, NOW.plusSeconds(1));
        log.input("bookingId = {}, request = {}, actor = {}", BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        ConflictException thrown = assertThrows(ConflictException.class,
                () -> service.reschedule(BOOKING_ID, change(NEW_START, NEW_END), OWNER));

        log.output("code = {}, pendingAmount = {}", thrown.code(), booking.getPendingAmount());
        assertEquals("RESCHEDULE_PAYMENT_PENDING", thrown.code());
        assertEquals(Long.valueOf(30_000L), booking.getPendingAmount());
    }

    // Viewpoint: VP3
    @Test
    public void test_reschedule_PendingPaymentExactExpiry_171() {
        // Cận: yêu cầu cũ hết hạn đúng lúc này (job chưa chạy): được đổi, yêu cầu cũ bị thay.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        setPending(booking, 30_000, NOW);
        log.input("bookingId = {}, request = {}, actor = {}", BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        RescheduleResult result = service.reschedule(BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        log.output("paymentRequired = {}, period = {}, hasPendingChange = {}", result.paymentRequired(), booking.getPeriod(),
                booking.hasPendingChange());
        assertFalse(result.paymentRequired());
        assertEquals(new TimeRange(NEW_START, NEW_END), booking.getPeriod());
        assertFalse(booking.hasPendingChange());
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_reschedule_OtherResource_172() {
        // Đổi sang vị trí khác cùng khung giờ: tiền thuê theo loại chỗ mới, đơn chuyển vị trí.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        SpaceResource meetingRoom = otherResource(ResourceTypeCode.MEETING_ROOM);
        when(pricing.quote(ResourceTypeCode.MEETING_ROOM, new TimeRange(START, END))).thenReturn(quote(RENTAL));
        RescheduleRequest request = new RescheduleRequest(OTHER_RESOURCE_ID, START, END);
        log.input("bookingId = {}, request = {}, actor = {}", BOOKING_ID, request, STAFF);

        RescheduleResult result = service.reschedule(BOOKING_ID, request, STAFF);

        log.output("resource = {}, period = {}, refund = {}", booking.getResource().getId(), booking.getPeriod(), result.refund());
        assertSame(meetingRoom, booking.getResource());
        assertEquals(new TimeRange(START, END), booking.getPeriod());
        assertEquals(Money.ZERO, result.refund());
        verify(resourceService).requireBookable(meetingRoom, 2);
        verify(notifications).bookingRescheduled(booking, Money.ZERO);
    }

    // Viewpoint: VP7
    @Test
    public void test_reschedule_OtherResourceNotFound_173() {
        // Vị trí mới không tồn tại.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        RescheduleRequest request = new RescheduleRequest(999L, NEW_START, NEW_END);
        log.input("bookingId = {}, request = {}, actor = {}", BOOKING_ID, request, OWNER);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.reschedule(BOOKING_ID, request, OWNER));

        log.output("code = {}, resource = {}", thrown.code(), booking.getResource().getId());
        assertEquals("NOT_FOUND", thrown.code());
        assertEquals(RESOURCE_ID, booking.getResource().getId());
    }

    // Viewpoint: VP4
    @Test
    public void test_reschedule_AddonAmountByNewBlocks_174() {
        // Add-on tính lại theo số block mới: 10.000 × 4 block × 2 cái = 80.000; chỗ mới đắt hơn → chờ trả phần chênh.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        booking.getAddons().add(new BookingAddon(booking, new Addon(branch, "A1", "Màn hình", 5, 10_000), 2, 10_000));
        addCharge(booking, ChargeType.ADDON, 40_000);
        booking.setPaidAmount(RENTAL + 40_000);
        LocalDateTime newEnd = NEW_START.plusHours(4);
        when(pricing.quote(any(), any())).thenReturn(new PriceQuote(4, 60, Money.vnd(400_000)));
        log.input("bookingId = {}, request = {}, actor = {}", BOOKING_ID, change(NEW_START, newEnd), OWNER);

        RescheduleResult result = service.reschedule(BOOKING_ID, change(NEW_START, newEnd), OWNER);

        log.output("amountDue = {}, addon = {}", result.amountDue(), booking.sumOf(ChargeType.ADDON));
        assertEquals(Money.vnd(400_000 + 80_000 - RENTAL - 40_000), result.amountDue());
        assertEquals(Money.vnd(40_000), booking.sumOf(ChargeType.ADDON));
    }

    // Viewpoint: VP3
    @Test
    public void test_reschedule_VoucherNotApplicable_175() {
        // Voucher của đơn không áp dụng cho chỗ mới: từ chối, đơn giữ nguyên.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        when(vouchers.quoteChange(any(), any(), any(), any())).thenThrow(new DomainException("VOUCHER_NOT_APPLICABLE", "sai loại"));
        log.input("bookingId = {}, request = {}, actor = {}", BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        DomainException thrown = assertThrows(DomainException.class, () -> service.reschedule(BOOKING_ID, change(NEW_START, NEW_END), OWNER));

        log.output("code = {}, period = {}", thrown.code(), booking.getPeriod());
        assertEquals("VOUCHER_NOT_APPLICABLE", thrown.code());
        assertEquals(new TimeRange(START, END), booking.getPeriod());
    }

    // Viewpoint: VP3, VP8
    @Test
    public void test_reschedule_AppliesVoucherAndMembership_176() {
        // Đổi ngay: ghi số tiền giảm mới vào lượt voucher, trả bớt phút gói theo thời lượng mới.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        addCharge(booking, ChargeType.VOUCHER_DISCOUNT, 20_000);
        booking.setPaidAmount(RENTAL - 20_000);
        when(vouchers.quoteChange(any(), any(), any(), any())).thenReturn(Money.vnd(20_000));
        log.input("bookingId = {}, request = {}, actor = {}", BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        RescheduleResult result = service.reschedule(BOOKING_ID, change(NEW_START, NEW_END), OWNER);

        log.output("voucher = {}, refund = {}", booking.sumOf(ChargeType.VOUCHER_DISCOUNT), result.refund());
        assertEquals(Money.vnd(20_000), booking.sumOf(ChargeType.VOUCHER_DISCOUNT));
        assertEquals(Money.ZERO, result.refund());
        verify(vouchers).applyChange(booking, Money.vnd(20_000));
        verify(memberships).applyChange(booking, quote(RENTAL));
    }

    // ---------- đổi chỗ chờ thanh toán: onPaymentSucceeded / onPaymentFailed / cancel ----------

    // Viewpoint: VP3, VP8
    @Test
    public void test_onPaymentSucceeded_PendingChangeExactAmount_177() {
        // Trả đúng phần chênh: đơn chuyển sang chỗ mới, cộng số đã trả, xóa yêu cầu chờ.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        setPending(booking, 50_000, NOW.plusMinutes(5));
        when(pricing.quote(any(), any())).thenReturn(quote(RENTAL + 50_000));
        Money paid = Money.vnd(50_000);
        log.input("bookingId = {}, paid = {}", BOOKING_ID, paid);

        service.onPaymentSucceeded(BOOKING_ID, paid);

        log.output("period = {}, paidAmount = {}, rental = {}, hasPendingChange = {}, status = {}", booking.getPeriod(),
                booking.getPaidAmount(), booking.sumOf(ChargeType.RENTAL), booking.hasPendingChange(), booking.getStatus());
        assertEquals(new TimeRange(NEW_START, NEW_END), booking.getPeriod());
        assertEquals(RENTAL + 50_000, booking.getPaidAmount());
        assertEquals(Money.vnd(RENTAL + 50_000), booking.sumOf(ChargeType.RENTAL));
        assertFalse(booking.hasPendingChange());
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        verify(notifications).bookingRescheduled(booking, Money.ZERO);
    }

    // Viewpoint: VP3
    @Test
    public void test_onPaymentSucceeded_PendingChangeAmountMismatch_178() {
        // Tiền về khác phần chênh (giao dịch của yêu cầu trước): hoàn số đó, yêu cầu hiện tại vẫn chờ.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        setPending(booking, 50_000, NOW.plusMinutes(5));
        Money paid = Money.vnd(49_999);
        log.input("bookingId = {}, paid = {}", BOOKING_ID, paid);

        service.onPaymentSucceeded(BOOKING_ID, paid);

        log.output("period = {}, paidAmount = {}, hasPendingChange = {}", booking.getPeriod(), booking.getPaidAmount(),
                booking.hasPendingChange());
        assertEquals(new TimeRange(START, END), booking.getPeriod());
        assertEquals(RENTAL, booking.getPaidAmount());
        assertTrue(booking.hasPendingChange());
        verify(refunds).request(booking, paid, "UNEXPECTED_PAYMENT", NOW);
    }

    // Viewpoint: VP3, VP8
    @Test
    public void test_onPaymentFailed_PendingChange_179() {
        // Trả phần chênh thất bại: bỏ yêu cầu đổi, đơn vẫn CONFIRMED ở chỗ cũ.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        setPending(booking, 50_000, NOW.plusMinutes(5));
        log.input("bookingId = {}", BOOKING_ID);

        service.onPaymentFailed(BOOKING_ID);

        log.output("status = {}, hasPendingChange = {}, period = {}", booking.getStatus(), booking.hasPendingChange(),
                booking.getPeriod());
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertFalse(booking.hasPendingChange());
        assertEquals(new TimeRange(START, END), booking.getPeriod());
        verify(notifications).rescheduleCancelled(booking);
        verify(vouchers, never()).release(any());
    }

    // Viewpoint: VP8
    @Test
    public void test_cancel_ClearsPendingChange_180() {
        // Hủy đơn đang có yêu cầu đổi chỗ chờ thanh toán: bỏ luôn yêu cầu, nhả chỗ mới.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        setPending(booking, 50_000, NOW.plusMinutes(5));
        log.input("bookingId = {}, actor = {}", BOOKING_ID, OWNER);

        Money refund = service.cancel(BOOKING_ID, OWNER);

        log.output("status = {}, hasPendingChange = {}, refund = {}", booking.getStatus(), booking.hasPendingChange(), refund);
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertFalse(booking.hasPendingChange());
        assertEquals(Money.vnd(RENTAL), refund);
    }

    // ---------- expirePendingChange ----------

    // Viewpoint: VP3
    @Test
    public void test_expirePendingChange_ExactExpiry_181() {
        // Cận dưới: đúng giờ hết hạn: hủy yêu cầu, báo khách.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        setPending(booking, 50_000, NOW);
        log.input("bookingId = {}", BOOKING_ID);

        service.expirePendingChange(BOOKING_ID);

        log.output("hasPendingChange = {}, pendingResource = {}", booking.hasPendingChange(), booking.getPendingResource());
        assertFalse(booking.hasPendingChange());
        assertNull(booking.getPendingResource());
        verify(bookings).save(booking);
        verify(notifications).rescheduleCancelled(booking);
    }

    // Viewpoint: VP3
    @Test
    public void test_expirePendingChange_OneSecondBeforeExpiry_182() {
        // Còn 1 giây: chưa hết hạn.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        setPending(booking, 50_000, NOW.plusSeconds(1));
        log.input("bookingId = {}", BOOKING_ID);

        DomainException thrown = assertThrows(DomainException.class, () -> service.expirePendingChange(BOOKING_ID));

        log.output("code = {}, hasPendingChange = {}", thrown.code(), booking.hasPendingChange());
        assertEquals("PENDING_CHANGE_NOT_EXPIRED", thrown.code());
        assertTrue(booking.hasPendingChange());
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_expirePendingChange_NoPendingChange_183() {
        // Đơn không có yêu cầu đổi chỗ (đã trả xong / đã bị hủy trước đó).
        paidBookingIn(BookingStatus.CONFIRMED);
        log.input("bookingId = {}", BOOKING_ID);

        DomainException thrown = assertThrows(DomainException.class, () -> service.expirePendingChange(BOOKING_ID));

        log.output("code = {}", thrown.code());
        assertEquals("NO_PENDING_CHANGE", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_expirePendingChange_BookingNotFound_184() {
        // Không có đơn.
        when(bookings.findById(BOOKING_ID)).thenReturn(Optional.empty());
        log.input("bookingId = {}", BOOKING_ID);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.expirePendingChange(BOOKING_ID));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // ---------- findExpiredPendingChangeIds ----------

    // Viewpoint: VP4
    @Test
    public void test_findExpiredPendingChangeIds_HasIds_185() {
        // Có 2 đơn quá hạn: trả đúng danh sách repository trả về tại thời điểm hiện tại.
        when(bookings.findPendingChangeIdsExpiredAt(NOW)).thenReturn(List.of(1L, 2L));
        log.input("now = {}", NOW);

        List<Long> ids = service.findExpiredPendingChangeIds();

        log.output("ids = {}", ids);
        assertEquals(List.of(1L, 2L), ids);
    }

    // Viewpoint: VP4
    @Test
    public void test_findExpiredPendingChangeIds_NoIds_186() {
        // Không có đơn nào.
        when(bookings.findPendingChangeIdsExpiredAt(NOW)).thenReturn(List.of());
        log.input("now = {}", NOW);

        List<Long> ids = service.findExpiredPendingChangeIds();

        log.output("ids = {}", ids);
        assertTrue(ids.isEmpty());
    }

    // ---------- checkIn ----------

    // Viewpoint: VP3
    @Test
    public void test_checkIn_ExactEarliest_080() {
        // BR-17: cận dưới: đúng 15 phút trước giờ bắt đầu.
        Booking booking = bookingIn(BookingStatus.CONFIRMED);
        LocalDateTime now = START.minusMinutes(15);
        clock.set(now);
        log.input("qrToken = {}", QR_TOKEN);

        Booking result = service.checkIn(QR_TOKEN);

        log.output("status = {}, checkedInAt = {}", result.getStatus(), result.getCheckedInAt());
        assertEquals(BookingStatus.CHECKED_IN, booking.getStatus());
        assertEquals(now, booking.getCheckedInAt());
    }

    // Viewpoint: VP3
    @Test
    public void test_checkIn_OneSecondTooEarly_081() {
        // Sớm hơn mốc 1 giây.
        Booking booking = bookingIn(BookingStatus.CONFIRMED);
        clock.set(START.minusMinutes(15).minusSeconds(1));
        log.input("qrToken = {}", QR_TOKEN);

        DomainException thrown = assertThrows(DomainException.class, () -> service.checkIn(QR_TOKEN));

        log.output("code = {}, status = {}", thrown.code(), booking.getStatus());
        assertEquals("CHECK_IN_TOO_EARLY", thrown.code());
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_checkIn_DuringPeriod_082() {
        // Khoảng giữa khung giờ.
        Booking booking = bookingIn(BookingStatus.CONFIRMED);
        clock.set(START.plusHours(1));
        log.input("qrToken = {}", QR_TOKEN);

        service.checkIn(QR_TOKEN);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.CHECKED_IN, booking.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_checkIn_LastSecond_083() {
        // Cận trên: trước giờ kết thúc 1 giây.
        Booking booking = bookingIn(BookingStatus.CONFIRMED);
        clock.set(END.minusSeconds(1));
        log.input("qrToken = {}", QR_TOKEN);

        service.checkIn(QR_TOKEN);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.CHECKED_IN, booking.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_checkIn_ExactEnd_084() {
        // Đúng giờ kết thúc: khoảng nửa mở nên đã quá muộn.
        bookingIn(BookingStatus.CONFIRMED);
        clock.set(END);
        log.input("qrToken = {}", QR_TOKEN);

        DomainException thrown = assertThrows(DomainException.class, () -> service.checkIn(QR_TOKEN));

        log.output("code = {}", thrown.code());
        assertEquals("CHECK_IN_TOO_LATE", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_checkIn_NotConfirmed_085() {
        // Đơn chưa thanh toán.
        bookingIn(BookingStatus.HELD);
        clock.set(START);
        log.input("qrToken = {}", QR_TOKEN);

        DomainException thrown = assertThrows(DomainException.class, () -> service.checkIn(QR_TOKEN));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_BOOKING_STATE", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_checkIn_NullToken_086() {
        // Mã QR null.
        String qrToken = null;
        log.input("qrToken = {}", qrToken);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.checkIn(qrToken));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP2
    @Test
    public void test_checkIn_EmptyToken_087() {
        // Mã QR rỗng.
        log.input("qrToken = {}", "");

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.checkIn(""));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP2
    @Test
    public void test_checkIn_Token1Byte_088() {
        // Mã QR ký tự 1 byte.
        Booking booking = bookingWithToken(TEXT_1_BYTE);
        clock.set(START);
        log.input("qrToken = {}", TEXT_1_BYTE);

        service.checkIn(TEXT_1_BYTE);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.CHECKED_IN, booking.getStatus());
    }

    // Viewpoint: VP2
    @Test
    public void test_checkIn_Token2Byte_089() {
        // Mã QR ký tự 2 byte.
        Booking booking = bookingWithToken(TEXT_2_BYTE);
        clock.set(START);
        log.input("qrToken = {}", TEXT_2_BYTE);

        service.checkIn(TEXT_2_BYTE);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.CHECKED_IN, booking.getStatus());
    }

    // Viewpoint: VP2
    @Test
    public void test_checkIn_Token3Byte_090() {
        // Mã QR ký tự 3 byte.
        Booking booking = bookingWithToken(TEXT_3_BYTE);
        clock.set(START);
        log.input("qrToken = {}", TEXT_3_BYTE);

        service.checkIn(TEXT_3_BYTE);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.CHECKED_IN, booking.getStatus());
    }

    // ---------- extend ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_extend_CheckedIn_091() {
        // UC08: gia hạn khi đang ngồi: dời giờ kết thúc, thêm khoản gia hạn, giữ trạng thái.
        Booking booking = bookingIn(BookingStatus.CHECKED_IN);
        clock.set(START.plusHours(1));
        when(pricing.quote(any(), any())).thenReturn(quote(100_000));
        LocalDateTime newEnd = END.plusHours(1);
        log.input("bookingId = {}, newEndAt = {}", BOOKING_ID, newEnd);

        Booking result = service.extend(BOOKING_ID, newEnd);

        log.output("endAt = {}, extension = {}, status = {}", result.getPeriod().endAt(), result.sumOf(ChargeType.EXTENSION), result.getStatus());
        assertEquals(newEnd, result.getPeriod().endAt());
        assertEquals(Money.vnd(100_000), result.sumOf(ChargeType.EXTENSION));
        assertEquals(BookingStatus.CHECKED_IN, result.getStatus());
        verify(pricing).quote(ResourceTypeCode.HOT_DESK, new TimeRange(END, newEnd));
    }

    // Viewpoint: VP3
    @Test
    public void test_extend_OneSecondAfterEnd_092() {
        // Cận dưới hợp lệ: dài thêm 1 giây.
        Booking booking = bookingIn(BookingStatus.CHECKED_IN);
        LocalDateTime newEnd = END.plusSeconds(1);
        log.input("bookingId = {}, newEndAt = {}", BOOKING_ID, newEnd);

        service.extend(BOOKING_ID, newEnd);

        log.output("endAt = {}", booking.getPeriod().endAt());
        assertEquals(newEnd, booking.getPeriod().endAt());
    }

    // Viewpoint: VP3
    @Test
    public void test_extend_EqualsOldEnd_093() {
        // Giờ mới bằng giờ kết thúc cũ.
        bookingIn(BookingStatus.CHECKED_IN);
        log.input("bookingId = {}, newEndAt = {}", BOOKING_ID, END);

        DomainException thrown = assertThrows(DomainException.class, () -> service.extend(BOOKING_ID, END));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_EXTENSION", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_extend_BeforeOldEnd_094() {
        // Giờ mới trước giờ kết thúc cũ.
        bookingIn(BookingStatus.CHECKED_IN);
        LocalDateTime newEnd = END.minusMinutes(1);
        log.input("bookingId = {}, newEndAt = {}", BOOKING_ID, newEnd);

        DomainException thrown = assertThrows(DomainException.class, () -> service.extend(BOOKING_ID, newEnd));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_EXTENSION", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_extend_NullNewEnd_095() {
        // Giờ mới null.
        bookingIn(BookingStatus.CHECKED_IN);
        LocalDateTime newEnd = null;
        log.input("bookingId = {}, newEndAt = {}", BOOKING_ID, newEnd);

        DomainException thrown = assertThrows(DomainException.class, () -> service.extend(BOOKING_ID, newEnd));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_EXTENSION", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_extend_SlotTaken_096() {
        // BR-07: có khách đặt tiếp trong khoảng gia hạn.
        Booking booking = bookingIn(BookingStatus.CHECKED_IN);
        LocalDateTime newEnd = END.plusHours(1);
        when(bookings.existsOccupying(RESOURCE_ID, new TimeRange(END, newEnd), BOOKING_ID)).thenReturn(true);
        log.input("bookingId = {}, newEndAt = {}", BOOKING_ID, newEnd);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.extend(BOOKING_ID, newEnd));

        log.output("code = {}, endAt = {}", thrown.code(), booking.getPeriod().endAt());
        assertEquals("EXTENSION_UNAVAILABLE", thrown.code());
        assertEquals(END, booking.getPeriod().endAt());
    }

    // Viewpoint: VP3
    @Test
    public void test_extend_OverstayFutureEnd_097() {
        // Đang quá giờ, gia hạn tới tương lai: trở lại CHECKED_IN.
        Booking booking = bookingIn(BookingStatus.OVERSTAY);
        LocalDateTime now = END.plusMinutes(30);
        clock.set(now);
        LocalDateTime newEnd = now.plusSeconds(1);
        log.input("bookingId = {}, newEndAt = {}", BOOKING_ID, newEnd);

        service.extend(BOOKING_ID, newEnd);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.CHECKED_IN, booking.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_extend_OverstayEndAtNow_098() {
        // Cận: giờ mới đúng bằng hiện tại: vẫn OVERSTAY.
        Booking booking = bookingIn(BookingStatus.OVERSTAY);
        LocalDateTime now = END.plusMinutes(30);
        clock.set(now);
        log.input("bookingId = {}, newEndAt = {}", BOOKING_ID, now);

        service.extend(BOOKING_ID, now);

        log.output("status = {}, endAt = {}", booking.getStatus(), booking.getPeriod().endAt());
        assertEquals(BookingStatus.OVERSTAY, booking.getStatus());
        assertEquals(now, booking.getPeriod().endAt());
    }

    // Viewpoint: VP3
    @Test
    public void test_extend_OverstayPastEnd_099() {
        // Gia hạn nhưng giờ mới vẫn ở quá khứ: vẫn OVERSTAY.
        Booking booking = bookingIn(BookingStatus.OVERSTAY);
        clock.set(END.plusMinutes(30));
        LocalDateTime newEnd = END.plusMinutes(10);
        log.input("bookingId = {}, newEndAt = {}", BOOKING_ID, newEnd);

        service.extend(BOOKING_ID, newEnd);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.OVERSTAY, booking.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_extend_InvalidState_100() {
        // Đơn chưa check-in.
        bookingIn(BookingStatus.CONFIRMED);
        LocalDateTime newEnd = END.plusHours(1);
        log.input("bookingId = {}, newEndAt = {}", BOOKING_ID, newEnd);

        DomainException thrown = assertThrows(DomainException.class, () -> service.extend(BOOKING_ID, newEnd));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_BOOKING_STATE", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_extend_BookingNotFound_101() {
        // Đơn không tồn tại.
        LocalDateTime newEnd = END.plusHours(1);
        log.input("bookingId = {}, newEndAt = {}", BOOKING_ID, newEnd);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.extend(BOOKING_ID, newEnd));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // ---------- checkOut ----------

    // Viewpoint: VP3
    @Test
    public void test_checkOut_OnTimePaidInFull_102() {
        // UC09: ra đúng giờ, đã trả đủ: hoàn tất.
        Booking booking = paidBookingIn(BookingStatus.CHECKED_IN);
        clock.set(END);
        log.input("qrToken = {}", QR_TOKEN);

        Booking result = service.checkOut(QR_TOKEN);

        log.output("status = {}, checkedOutAt = {}", result.getStatus(), result.getCheckedOutAt());
        assertEquals(BookingStatus.COMPLETED, booking.getStatus());
        assertEquals(END, booking.getCheckedOutAt());
        verify(pricing, never()).overstaySurcharge(any(), any());
    }

    // Viewpoint: VP3
    @Test
    public void test_checkOut_ExactGrace_103() {
        // BR-06: cận trên của ân hạn: đúng 5 phút, không phụ phí.
        Booking booking = paidBookingIn(BookingStatus.CHECKED_IN);
        clock.set(END.plusMinutes(5));
        log.input("qrToken = {}", QR_TOKEN);

        service.checkOut(QR_TOKEN);

        log.output("status = {}, surcharge = {}", booking.getStatus(), booking.sumOf(ChargeType.OVERSTAY_SURCHARGE));
        assertEquals(BookingStatus.COMPLETED, booking.getStatus());
        assertEquals(Money.ZERO, booking.sumOf(ChargeType.OVERSTAY_SURCHARGE));
    }

    // Viewpoint: VP3
    @Test
    public void test_checkOut_OneSecondAfterGrace_104() {
        // Quá ân hạn 1 giây: thêm phụ phí, chờ thu nốt.
        Booking booking = paidBookingIn(BookingStatus.CHECKED_IN);
        LocalDateTime now = END.plusMinutes(5).plusSeconds(1);
        clock.set(now);
        when(pricing.overstaySurcharge(ResourceTypeCode.HOT_DESK, new TimeRange(END, now))).thenReturn(Money.vnd(50_000));
        log.input("qrToken = {}", QR_TOKEN);

        service.checkOut(QR_TOKEN);

        log.output("status = {}, surcharge = {}, outstanding = {}", booking.getStatus(), booking.sumOf(ChargeType.OVERSTAY_SURCHARGE), booking.getOutstanding());
        assertEquals(BookingStatus.CHECKED_OUT, booking.getStatus());
        assertEquals(Money.vnd(50_000), booking.sumOf(ChargeType.OVERSTAY_SURCHARGE));
        assertEquals(Money.vnd(50_000), booking.getOutstanding());
    }

    // Viewpoint: VP3
    @Test
    public void test_checkOut_ZeroSurcharge_105() {
        // Quá ân hạn nhưng phụ phí 0 đồng: không thêm dòng.
        Booking booking = paidBookingIn(BookingStatus.CHECKED_IN);
        clock.set(END.plusMinutes(10));
        when(pricing.overstaySurcharge(any(), any())).thenReturn(Money.ZERO);
        log.input("qrToken = {}", QR_TOKEN);

        service.checkOut(QR_TOKEN);

        log.output("status = {}, chargeCount = {}", booking.getStatus(), booking.getCharges().size());
        assertEquals(BookingStatus.COMPLETED, booking.getStatus());
        assertEquals(1, booking.getCharges().size());
    }

    // Viewpoint: VP3
    @Test
    public void test_checkOut_OutstandingExtension_106() {
        // Còn nợ tiền gia hạn: CHECKED_OUT dù ra đúng giờ.
        Booking booking = paidBookingIn(BookingStatus.CHECKED_IN);
        addCharge(booking, ChargeType.EXTENSION, 30_000);
        clock.set(END);
        log.input("qrToken = {}", QR_TOKEN);

        service.checkOut(QR_TOKEN);

        log.output("status = {}, outstanding = {}", booking.getStatus(), booking.getOutstanding());
        assertEquals(BookingStatus.CHECKED_OUT, booking.getStatus());
        assertEquals(Money.vnd(30_000), booking.getOutstanding());
    }

    // Viewpoint: VP3
    @Test
    public void test_checkOut_FromOverstay_107() {
        // Trạng thái OVERSTAY cũng được check-out.
        Booking booking = paidBookingIn(BookingStatus.OVERSTAY);
        clock.set(END.plusMinutes(5));
        log.input("qrToken = {}", QR_TOKEN);

        service.checkOut(QR_TOKEN);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.COMPLETED, booking.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_checkOut_InvalidState_108() {
        // Đơn chưa check-in.
        bookingIn(BookingStatus.CONFIRMED);
        log.input("qrToken = {}", QR_TOKEN);

        DomainException thrown = assertThrows(DomainException.class, () -> service.checkOut(QR_TOKEN));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_BOOKING_STATE", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_checkOut_NullToken_109() {
        // Mã QR null.
        String qrToken = null;
        log.input("qrToken = {}", qrToken);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.checkOut(qrToken));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP2
    @Test
    public void test_checkOut_EmptyToken_110() {
        // Mã QR rỗng.
        log.input("qrToken = {}", "");

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.checkOut(""));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP2
    @Test
    public void test_checkOut_Token1Byte_111() {
        // Mã QR ký tự 1 byte.
        Booking booking = bookingWithToken(TEXT_1_BYTE);
        booking.setStatus(BookingStatus.CHECKED_IN);
        booking.setPaidAmount(RENTAL);
        clock.set(END);
        log.input("qrToken = {}", TEXT_1_BYTE);

        service.checkOut(TEXT_1_BYTE);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.COMPLETED, booking.getStatus());
    }

    // Viewpoint: VP2
    @Test
    public void test_checkOut_Token2Byte_112() {
        // Mã QR ký tự 2 byte.
        Booking booking = bookingWithToken(TEXT_2_BYTE);
        booking.setStatus(BookingStatus.CHECKED_IN);
        booking.setPaidAmount(RENTAL);
        clock.set(END);
        log.input("qrToken = {}", TEXT_2_BYTE);

        service.checkOut(TEXT_2_BYTE);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.COMPLETED, booking.getStatus());
    }

    // Viewpoint: VP2
    @Test
    public void test_checkOut_Token3Byte_113() {
        // Mã QR ký tự 3 byte.
        Booking booking = bookingWithToken(TEXT_3_BYTE);
        booking.setStatus(BookingStatus.CHECKED_IN);
        booking.setPaidAmount(RENTAL);
        clock.set(END);
        log.input("qrToken = {}", TEXT_3_BYTE);

        service.checkOut(TEXT_3_BYTE);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.COMPLETED, booking.getStatus());
    }

    // ---------- settleOutstanding ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_settleOutstanding_ExactAmount_114() {
        // UC16: thu đúng số còn nợ: hoàn tất.
        Booking booking = checkedOutWithDebt(50_000);
        Money amount = Money.vnd(50_000);
        log.input("bookingId = {}, amount = {}", BOOKING_ID, amount);

        Booking result = service.settleOutstanding(BOOKING_ID, amount);

        log.output("status = {}, paidAmount = {}, outstanding = {}", result.getStatus(), result.getPaidAmount(), result.getOutstanding());
        assertEquals(BookingStatus.COMPLETED, booking.getStatus());
        assertEquals(RENTAL + 50_000, booking.getPaidAmount());
        assertEquals(Money.ZERO, booking.getOutstanding());
    }

    // Viewpoint: VP3
    @Test
    public void test_settleOutstanding_OneBelow_115() {
        // Cận dưới: thiếu 1 đồng.
        Booking booking = checkedOutWithDebt(50_000);
        Money amount = Money.vnd(49_999);
        log.input("bookingId = {}, amount = {}", BOOKING_ID, amount);

        DomainException thrown = assertThrows(DomainException.class, () -> service.settleOutstanding(BOOKING_ID, amount));

        log.output("code = {}, status = {}", thrown.code(), booking.getStatus());
        assertEquals("AMOUNT_MISMATCH", thrown.code());
        assertEquals(BookingStatus.CHECKED_OUT, booking.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_settleOutstanding_OneAbove_116() {
        // Cận trên: thừa 1 đồng.
        checkedOutWithDebt(50_000);
        Money amount = Money.vnd(50_001);
        log.input("bookingId = {}, amount = {}", BOOKING_ID, amount);

        DomainException thrown = assertThrows(DomainException.class, () -> service.settleOutstanding(BOOKING_ID, amount));

        log.output("code = {}", thrown.code());
        assertEquals("AMOUNT_MISMATCH", thrown.code());
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_settleOutstanding_ZeroAmountNoDebt_117() {
        // Số tiền nhỏ nhất: không còn nợ, thu 0 đồng vẫn hoàn tất.
        Booking booking = paidBookingIn(BookingStatus.CHECKED_OUT);
        log.input("bookingId = {}, amount = {}", BOOKING_ID, Money.ZERO);

        service.settleOutstanding(BOOKING_ID, Money.ZERO);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.COMPLETED, booking.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_settleOutstanding_MaxAmount_118() {
        // Số tiền lớn nhất của kiểu long: sai số tiền.
        checkedOutWithDebt(50_000);
        Money amount = Money.vnd(Long.MAX_VALUE);
        log.input("bookingId = {}, amount = {}", BOOKING_ID, amount);

        DomainException thrown = assertThrows(DomainException.class, () -> service.settleOutstanding(BOOKING_ID, amount));

        log.output("code = {}", thrown.code());
        assertEquals("AMOUNT_MISMATCH", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_settleOutstanding_InvalidState_119() {
        // Đơn chưa check-out.
        bookingIn(BookingStatus.CHECKED_IN);
        Money amount = Money.vnd(50_000);
        log.input("bookingId = {}, amount = {}", BOOKING_ID, amount);

        DomainException thrown = assertThrows(DomainException.class, () -> service.settleOutstanding(BOOKING_ID, amount));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_BOOKING_STATE", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_settleOutstanding_BookingNotFound_120() {
        // Đơn không tồn tại.
        Money amount = Money.vnd(50_000);
        log.input("bookingId = {}, amount = {}", BOOKING_ID, amount);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.settleOutstanding(BOOKING_ID, amount));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_settleOutstanding_NullAmount_121() {
        // Số tiền null.
        checkedOutWithDebt(50_000);
        Money amount = null;
        log.input("bookingId = {}, amount = {}", BOOKING_ID, amount);

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.settleOutstanding(BOOKING_ID, amount));

        log.output("exception = {}", thrown.getClass().getSimpleName());
    }

    // ---------- get(bookingId, actor) ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_get_Owner_122() {
        // Chủ đơn xem được đơn của mình.
        Booking booking = bookingIn(BookingStatus.CONFIRMED);
        log.input("bookingId = {}, actor = {}", BOOKING_ID, OWNER);

        Booking result = service.get(BOOKING_ID, OWNER);

        log.output("id = {}", result.getId());
        assertSame(booking, result);
    }

    // Viewpoint: VP3
    @Test
    public void test_get_Staff_123() {
        // Nhân viên xem được mọi đơn.
        Booking booking = bookingIn(BookingStatus.CONFIRMED);
        log.input("bookingId = {}, actor = {}", BOOKING_ID, STAFF);

        Booking result = service.get(BOOKING_ID, STAFF);

        log.output("id = {}", result.getId());
        assertSame(booking, result);
    }

    // Viewpoint: VP3
    @Test
    public void test_get_OtherCustomer_124() {
        // Đơn của người khác trả 404.
        bookingIn(BookingStatus.CONFIRMED);
        log.input("bookingId = {}, actor = {}", BOOKING_ID, OTHER_CUSTOMER);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.get(BOOKING_ID, OTHER_CUSTOMER));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_get_Guest_125() {
        // Khách vãng lai không xem được đơn.
        bookingIn(BookingStatus.CONFIRMED);
        log.input("bookingId = {}, actor = {}", BOOKING_ID, Actor.GUEST);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.get(BOOKING_ID, Actor.GUEST));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_get_NullActor_126() {
        // Actor null.
        bookingIn(BookingStatus.CONFIRMED);
        Actor actor = null;
        log.input("bookingId = {}, actor = {}", BOOKING_ID, actor);

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.get(BOOKING_ID, actor));

        log.output("exception = {}", thrown.getClass().getSimpleName());
    }

    // ---------- get(bookingId) ----------

    // Viewpoint: VP7
    @Test
    public void test_get_InternalFound_127() {
        // Dùng nội bộ: có đơn.
        Booking booking = bookingIn(BookingStatus.CONFIRMED);
        log.input("bookingId = {}", BOOKING_ID);

        Booking result = service.get(BOOKING_ID);

        log.output("id = {}", result.getId());
        assertSame(booking, result);
    }

    // Viewpoint: VP7
    @Test
    public void test_get_InternalNotFound_128() {
        // Dùng nội bộ: không có đơn.
        log.input("bookingId = {}", BOOKING_ID);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.get(BOOKING_ID));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_get_InternalNullId_129() {
        // Dùng nội bộ: mã đơn null.
        Long bookingId = null;
        log.input("bookingId = {}", bookingId);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.get(bookingId));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // ---------- listMine ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_listMine_CustomerWithoutStatus_130() {
        // Không lọc trạng thái: chỉ lọc theo khách, đơn mới nhất trước.
        Page<Booking> page = stubFindAll();
        BookingStatus status = null;
        log.input("actor = {}, status = {}, page = {}, size = {}", OWNER, status, 0, 20);

        Page<Booking> result = service.listMine(OWNER, status, 0, 20);

        Pageable pageable = capturedPageable();
        int predicates = predicateCount(runSpec());
        log.output("predicates = {}, pageNumber = {}, pageSize = {}, sort = {}",
                predicates, pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort());
        assertSame(page, result);
        assertEquals(1, predicates);
        assertEquals(0, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());
        assertEquals(Sort.by(Sort.Order.desc("period.startAt"), Sort.Order.desc("id")), pageable.getSort());
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_listMine_CustomerWithStatus_131() {
        // Lọc thêm theo trạng thái.
        stubFindAll();
        log.input("actor = {}, status = {}, page = {}, size = {}", OWNER, BookingStatus.CONFIRMED, 0, 20);

        service.listMine(OWNER, BookingStatus.CONFIRMED, 0, 20);

        CriteriaBuilder cb = runSpec();
        int predicates = predicateCount(cb);
        log.output("predicates = {}", predicates);
        assertEquals(2, predicates);
        verify(cb).equal(any(Expression.class), eq(CUSTOMER_ID));
        verify(cb).equal(any(Expression.class), eq(BookingStatus.CONFIRMED));
    }

    // Viewpoint: VP3
    @Test
    public void test_listMine_Guest_132() {
        // Khách vãng lai không có "đơn của tôi".
        log.input("actor = {}, status = {}, page = {}, size = {}", Actor.GUEST, null, 0, 20);

        DomainException thrown = assertThrows(DomainException.class, () -> service.listMine(Actor.GUEST, null, 0, 20));

        log.output("code = {}", thrown.code());
        assertEquals("CUSTOMER_ONLY", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_listMine_Staff_133() {
        // Nhân viên không có "đơn của tôi".
        log.input("actor = {}, status = {}, page = {}, size = {}", STAFF, null, 0, 20);

        DomainException thrown = assertThrows(DomainException.class, () -> service.listMine(STAFF, null, 0, 20));

        log.output("code = {}", thrown.code());
        assertEquals("CUSTOMER_ONLY", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_listMine_NullActor_134() {
        // Actor null.
        Actor actor = null;
        log.input("actor = {}, status = {}, page = {}, size = {}", actor, null, 0, 20);

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.listMine(actor, null, 0, 20));

        log.output("exception = {}", thrown.getClass().getSimpleName());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_listMine_PageNegative_135() {
        // Cận dưới sai của page.
        log.input("actor = {}, status = {}, page = {}, size = {}", OWNER, null, -1, 20);

        DomainException thrown = assertThrows(DomainException.class, () -> service.listMine(OWNER, null, -1, 20));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_PAGE", thrown.code());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_listMine_SizeZero_136() {
        // Cận dưới sai của size.
        log.input("actor = {}, status = {}, page = {}, size = {}", OWNER, null, 0, 0);

        DomainException thrown = assertThrows(DomainException.class, () -> service.listMine(OWNER, null, 0, 0));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_PAGE", thrown.code());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_listMine_SizeOne_137() {
        // Cận dưới đúng của size.
        stubFindAll();
        log.input("actor = {}, status = {}, page = {}, size = {}", OWNER, null, 0, 1);

        service.listMine(OWNER, null, 0, 1);

        Pageable pageable = capturedPageable();
        log.output("pageSize = {}", pageable.getPageSize());
        assertEquals(1, pageable.getPageSize());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_listMine_SizeMax_138() {
        // Cận trên đúng của size.
        stubFindAll();
        log.input("actor = {}, status = {}, page = {}, size = {}", OWNER, null, 0, 100);

        service.listMine(OWNER, null, 0, 100);

        Pageable pageable = capturedPageable();
        log.output("pageSize = {}", pageable.getPageSize());
        assertEquals(100, pageable.getPageSize());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_listMine_SizeOverMax_139() {
        // Cận trên sai của size.
        log.input("actor = {}, status = {}, page = {}, size = {}", OWNER, null, 0, 101);

        DomainException thrown = assertThrows(DomainException.class, () -> service.listMine(OWNER, null, 0, 101));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_PAGE", thrown.code());
    }

    // Viewpoint: VP1
    @Test
    public void test_listMine_PageMaxValue_140() {
        // Số trang lớn nhất của kiểu int.
        stubFindAll();
        log.input("actor = {}, status = {}, page = {}, size = {}", OWNER, null, Integer.MAX_VALUE, 20);

        service.listMine(OWNER, null, Integer.MAX_VALUE, 20);

        Pageable pageable = capturedPageable();
        log.output("pageNumber = {}", pageable.getPageNumber());
        assertEquals(Integer.MAX_VALUE, pageable.getPageNumber());
    }

    // ---------- search ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_search_EmptyFilter_141() {
        // Bộ lọc rỗng: không có điều kiện nào.
        Page<Booking> page = stubFindAll();
        BookingFilter filter = new BookingFilter(null, null, null, null, null);
        log.input("filter = {}, page = {}, size = {}", filter, 0, 20);

        Page<Booking> result = service.search(filter, 0, 20);

        int predicates = predicateCount(runSpec());
        log.output("predicates = {}", predicates);
        assertSame(page, result);
        assertEquals(0, predicates);
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_search_AllFilters_142() {
        // Đủ điều kiện: khách, SĐT, trạng thái, from, to.
        stubFindAll();
        BookingFilter filter = new BookingFilter(CUSTOMER_ID, "0911222333", BookingStatus.CONFIRMED, START, END);
        log.input("filter = {}, page = {}, size = {}", filter, 0, 20);

        service.search(filter, 0, 20);

        int predicates = predicateCount(runSpec());
        log.output("predicates = {}", predicates);
        assertEquals(5, predicates);
    }

    // Viewpoint: VP2, VP3
    @Test
    public void test_search_EmptyPhone_143() {
        // SĐT rỗng: không lọc theo SĐT.
        stubFindAll();
        BookingFilter filter = new BookingFilter(null, "", null, null, null);
        log.input("filter = {}, page = {}, size = {}", filter, 0, 20);

        service.search(filter, 0, 20);

        int predicates = predicateCount(runSpec());
        log.output("predicates = {}", predicates);
        assertEquals(0, predicates);
    }

    // Viewpoint: VP2, VP3
    @Test
    public void test_search_BlankPhone_144() {
        // SĐT chỉ có khoảng trắng: không lọc theo SĐT.
        stubFindAll();
        BookingFilter filter = new BookingFilter(null, "   ", null, null, null);
        log.input("filter = {}, page = {}, size = {}", filter, 0, 20);

        service.search(filter, 0, 20);

        int predicates = predicateCount(runSpec());
        log.output("predicates = {}", predicates);
        assertEquals(0, predicates);
    }

    // Viewpoint: VP2, VP3
    @Test
    public void test_search_PhoneTrimmed_145() {
        // SĐT được bỏ khoảng trắng hai đầu trước khi lọc.
        stubFindAll();
        BookingFilter filter = new BookingFilter(null, " 0911222333 ", null, null, null);
        log.input("filter = {}, page = {}, size = {}", filter, 0, 20);

        service.search(filter, 0, 20);

        CriteriaBuilder cb = runSpec();
        int predicates = predicateCount(cb);
        log.output("predicates = {}", predicates);
        assertEquals(1, predicates);
        verify(cb).equal(any(Expression.class), eq("0911222333"));
    }

    // Viewpoint: VP2
    @Test
    public void test_search_Phone2Byte_146() {
        // SĐT ký tự 2 byte vẫn được dùng làm điều kiện lọc.
        stubFindAll();
        BookingFilter filter = new BookingFilter(null, TEXT_2_BYTE, null, null, null);
        log.input("filter = {}, page = {}, size = {}", filter, 0, 20);

        service.search(filter, 0, 20);

        int predicates = predicateCount(runSpec());
        log.output("predicates = {}", predicates);
        assertEquals(1, predicates);
    }

    // Viewpoint: VP2
    @Test
    public void test_search_Phone3Byte_147() {
        // SĐT ký tự 3 byte vẫn được dùng làm điều kiện lọc.
        stubFindAll();
        BookingFilter filter = new BookingFilter(null, TEXT_3_BYTE, null, null, null);
        log.input("filter = {}, page = {}, size = {}", filter, 0, 20);

        service.search(filter, 0, 20);

        int predicates = predicateCount(runSpec());
        log.output("predicates = {}", predicates);
        assertEquals(1, predicates);
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_search_OnlyFrom_148() {
        // Chỉ có from: không kiểm tra khoảng, lọc một điều kiện.
        stubFindAll();
        BookingFilter filter = new BookingFilter(null, null, null, START, null);
        log.input("filter = {}, page = {}, size = {}", filter, 0, 20);

        service.search(filter, 0, 20);

        int predicates = predicateCount(runSpec());
        log.output("predicates = {}", predicates);
        assertEquals(1, predicates);
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_search_OnlyTo_149() {
        // Chỉ có to: không kiểm tra khoảng, lọc một điều kiện.
        stubFindAll();
        BookingFilter filter = new BookingFilter(null, null, null, null, END);
        log.input("filter = {}, page = {}, size = {}", filter, 0, 20);

        service.search(filter, 0, 20);

        int predicates = predicateCount(runSpec());
        log.output("predicates = {}", predicates);
        assertEquals(1, predicates);
    }

    // Viewpoint: VP3
    @Test
    public void test_search_ToOneSecondAfterFrom_150() {
        // Cận dưới đúng: to sau from 1 giây.
        Page<Booking> page = stubFindAll();
        BookingFilter filter = new BookingFilter(null, null, null, START, START.plusSeconds(1));
        log.input("filter = {}, page = {}, size = {}", filter, 0, 20);

        Page<Booking> result = service.search(filter, 0, 20);

        log.output("totalElements = {}", result.getTotalElements());
        assertSame(page, result);
    }

    // Viewpoint: VP3
    @Test
    public void test_search_ToEqualsFrom_151() {
        // Cận sai: to bằng from.
        BookingFilter filter = new BookingFilter(null, null, null, START, START);
        log.input("filter = {}, page = {}, size = {}", filter, 0, 20);

        DomainException thrown = assertThrows(DomainException.class, () -> service.search(filter, 0, 20));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_TIME_RANGE", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_search_ToBeforeFrom_152() {
        // to trước from.
        BookingFilter filter = new BookingFilter(null, null, null, END, START);
        log.input("filter = {}, page = {}, size = {}", filter, 0, 20);

        DomainException thrown = assertThrows(DomainException.class, () -> service.search(filter, 0, 20));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_TIME_RANGE", thrown.code());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_search_InvalidPage_153() {
        // Phân trang sai.
        BookingFilter filter = new BookingFilter(null, null, null, null, null);
        log.input("filter = {}, page = {}, size = {}", filter, -1, 20);

        DomainException thrown = assertThrows(DomainException.class, () -> service.search(filter, -1, 20));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_PAGE", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_search_NullFilter_154() {
        // Bộ lọc null.
        BookingFilter filter = null;
        log.input("filter = {}, page = {}, size = {}", filter, 0, 20);

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.search(filter, 0, 20));

        log.output("exception = {}", thrown.getClass().getSimpleName());
    }

    // ---------- expireHold ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_expireHold_ExactExpiry_155() {
        // Cận: đúng lúc hết hạn: giải phóng chỗ, trả lại ưu đãi, báo khách.
        Booking booking = bookingIn(BookingStatus.HELD);
        booking.setHoldExpiresAt(NOW);
        log.input("bookingId = {}", BOOKING_ID);

        service.expireHold(BOOKING_ID);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.EXPIRED, booking.getStatus());
        verify(vouchers).release(booking);
        verify(memberships).release(booking);
        verify(notifications).holdExpired(booking);
    }

    // Viewpoint: VP3
    @Test
    public void test_expireHold_AfterExpiry_156() {
        // Đã quá hạn.
        Booking booking = bookingIn(BookingStatus.HELD);
        booking.setHoldExpiresAt(NOW.minusMinutes(1));
        log.input("bookingId = {}", BOOKING_ID);

        service.expireHold(BOOKING_ID);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.EXPIRED, booking.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_expireHold_OneSecondBeforeExpiry_157() {
        // Còn 1 giây: chưa hết hạn.
        Booking booking = bookingIn(BookingStatus.HELD);
        booking.setHoldExpiresAt(NOW.plusSeconds(1));
        log.input("bookingId = {}", BOOKING_ID);

        DomainException thrown = assertThrows(DomainException.class, () -> service.expireHold(BOOKING_ID));

        log.output("code = {}, status = {}", thrown.code(), booking.getStatus());
        assertEquals("HOLD_NOT_EXPIRED", thrown.code());
        assertEquals(BookingStatus.HELD, booking.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_expireHold_NotHeld_158() {
        // Đơn không ở HELD.
        bookingIn(BookingStatus.CONFIRMED);
        log.input("bookingId = {}", BOOKING_ID);

        DomainException thrown = assertThrows(DomainException.class, () -> service.expireHold(BOOKING_ID));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_BOOKING_STATE", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_expireHold_NullExpiry_159() {
        // Đơn HELD thiếu hạn giữ chỗ.
        bookingIn(BookingStatus.HELD);
        log.input("bookingId = {}", BOOKING_ID);

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.expireHold(BOOKING_ID));

        log.output("exception = {}", thrown.getClass().getSimpleName());
    }

    // Viewpoint: VP7
    @Test
    public void test_expireHold_BookingNotFound_160() {
        // Đơn không tồn tại.
        log.input("bookingId = {}", BOOKING_ID);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.expireHold(BOOKING_ID));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // ---------- flagOverstay ----------

    // Viewpoint: VP3
    @Test
    public void test_flagOverstay_OneSecondAfterGrace_161() {
        // Quá ân hạn 1 giây: đánh dấu OVERSTAY.
        Booking booking = bookingIn(BookingStatus.CHECKED_IN);
        clock.set(END.plusMinutes(5).plusSeconds(1));
        log.input("bookingId = {}", BOOKING_ID);

        service.flagOverstay(BOOKING_ID);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.OVERSTAY, booking.getStatus());
        verify(bookings).save(booking);
    }

    // Viewpoint: VP3
    @Test
    public void test_flagOverstay_ExactGrace_162() {
        // Cận: đúng hết ân hạn: chưa đánh dấu.
        Booking booking = bookingIn(BookingStatus.CHECKED_IN);
        clock.set(END.plusMinutes(5));
        log.input("bookingId = {}", BOOKING_ID);

        service.flagOverstay(BOOKING_ID);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.CHECKED_IN, booking.getStatus());
        verify(bookings, never()).save(any());
    }

    // Viewpoint: VP3
    @Test
    public void test_flagOverstay_NotCheckedIn_163() {
        // Đơn không ở CHECKED_IN: không đánh dấu dù đã quá giờ.
        Booking booking = bookingIn(BookingStatus.COMPLETED);
        clock.set(END.plusHours(1));
        log.input("bookingId = {}", BOOKING_ID);

        service.flagOverstay(BOOKING_ID);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.COMPLETED, booking.getStatus());
        verify(bookings, never()).save(any());
    }

    // Viewpoint: VP7
    @Test
    public void test_flagOverstay_BookingNotFound_164() {
        // Đơn không tồn tại.
        log.input("bookingId = {}", BOOKING_ID);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.flagOverstay(BOOKING_ID));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // ---------- findExpiredHoldIds ----------

    // Viewpoint: VP7
    @Test
    public void test_findExpiredHoldIds_HasIds_165() {
        // Trả danh sách đơn quá hạn tính theo giờ hiện tại.
        when(bookings.findHeldIdsExpiredAt(NOW)).thenReturn(List.of(1L, 2L));
        log.input("now = {}", NOW);

        List<Long> ids = service.findExpiredHoldIds();

        log.output("ids = {}", ids);
        assertEquals(List.of(1L, 2L), ids);
    }

    // Viewpoint: VP7
    @Test
    public void test_findExpiredHoldIds_NoIds_166() {
        // Không có đơn quá hạn.
        log.input("now = {}", NOW);

        List<Long> ids = service.findExpiredHoldIds();

        log.output("ids = {}", ids);
        assertTrue(ids.isEmpty());
    }

    // ---------- findOverdueIds ----------

    // Viewpoint: VP7
    @Test
    public void test_findOverdueIds_HasIds_167() {
        // Mốc so sánh là giờ hiện tại trừ thời gian ân hạn.
        when(bookings.findCheckedInIdsEndedBefore(NOW.minusMinutes(5))).thenReturn(List.of(3L));
        log.input("now = {}", NOW);

        List<Long> ids = service.findOverdueIds();

        log.output("ids = {}", ids);
        assertEquals(List.of(3L), ids);
    }

    // Viewpoint: VP7
    @Test
    public void test_findOverdueIds_NoIds_168() {
        // Không có đơn quá giờ.
        log.input("now = {}", NOW);

        List<Long> ids = service.findOverdueIds();

        log.output("ids = {}", ids);
        assertTrue(ids.isEmpty());
    }

    // Viewpoint: VP7
    @Test
    public void test_hold_ContactWithEmail_169() {
        // Liên hệ có email được chuyển sang ContactInfo; đơn không có thời điểm check-in.
        ContactRequest contact = new ContactRequest("Lê C", "0911222333", "c@example.com");
        HoldBookingRequest request = new HoldBookingRequest(RESOURCE_ID, START, END, 2, contact);
        log.input("request = {}, actor = {}", request, Actor.GUEST);

        Booking booking = service.hold(request, Actor.GUEST);

        log.output("checkedInAt = {}", booking.getCheckedInAt());
        assertNull(booking.getCheckedInAt());
        verify(customers).registerOrUpdate(contact.toDomain());
    }

    // ---------- flagOverstay: báo quầy (notification.md) ----------

    // Viewpoint: VP3, VP8
    @Test
    public void test_flagOverstay_NotifiesFrontDesk_187() {
        // Vừa chuyển OVERSTAY thì báo lễ tân, sau khi lưu.
        Booking booking = bookingIn(BookingStatus.CHECKED_IN);
        clock.set(END.plusMinutes(5).plusSeconds(1));
        log.input("bookingId = {}", BOOKING_ID);

        service.flagOverstay(BOOKING_ID);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.OVERSTAY, booking.getStatus());
        InOrder order = inOrder(bookings, notifications);
        order.verify(bookings).save(booking);
        order.verify(notifications).overstay(booking);
    }

    // Viewpoint: VP3, VP8
    @Test
    public void test_flagOverstay_NotOverdueNoNotification_188() {
        // Chưa quá ân hạn: không báo quầy.
        Booking booking = bookingIn(BookingStatus.CHECKED_IN);
        clock.set(END.plusMinutes(5));
        log.input("bookingId = {}", BOOKING_ID);

        service.flagOverstay(BOOKING_ID);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.CHECKED_IN, booking.getStatus());
        verify(notifications, never()).overstay(any());
    }

    // ---------- markNoShow (BR-16) ----------

    // Viewpoint: VP3
    @Test
    public void test_markNoShow_OneSecondAfterThirtyMinutes_189() {
        // Đơn 2 giờ: chờ min(30 phút, 60 phút) = 30 phút; quá 1 giây → NO_SHOW, lưu, báo khách,
        // không trả lượt voucher / phút gói, không hoàn tiền.
        Booking booking = paidBookingIn(BookingStatus.CONFIRMED);
        clock.set(START.plusMinutes(30).plusSeconds(1));
        log.input("bookingId = {}", BOOKING_ID);

        service.markNoShow(BOOKING_ID);

        log.output("status = {}, historySize = {}", booking.getStatus(), booking.getStatusHistory().size());
        assertEquals(BookingStatus.NO_SHOW, booking.getStatus());
        assertEquals(1, booking.getStatusHistory().size());
        verify(bookings).save(booking);
        verify(notifications).noShow(booking);
        verify(vouchers, never()).release(any());
        verify(memberships, never()).release(any());
        verify(refunds, never()).request(any(), any(), any(), any());
    }

    // Viewpoint: VP3
    @Test
    public void test_markNoShow_ExactlyThirtyMinutes_190() {
        // Cận: đúng 30 phút sau giờ bắt đầu → chưa đánh dấu.
        Booking booking = bookingIn(BookingStatus.CONFIRMED);
        clock.set(START.plusMinutes(30));
        log.input("bookingId = {}", BOOKING_ID);

        service.markNoShow(BOOKING_ID);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        verify(bookings, never()).save(any());
        verify(notifications, never()).noShow(any());
    }

    // Viewpoint: VP3
    @Test
    public void test_markNoShow_ShortBookingUsesHalfDuration_191() {
        // Đơn 40 phút: 50% = 20 phút < 30 phút → quá 20 phút 1 giây là NO_SHOW.
        Booking booking = bookingIn(BookingStatus.CONFIRMED);
        booking.setPeriod(new TimeRange(START, START.plusMinutes(40)));
        clock.set(START.plusMinutes(20).plusSeconds(1));
        log.input("bookingId = {}", BOOKING_ID);

        service.markNoShow(BOOKING_ID);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.NO_SHOW, booking.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_markNoShow_ShortBookingAtHalfDuration_192() {
        // Cận: đơn 40 phút, đúng 20 phút sau giờ bắt đầu → chưa đánh dấu.
        Booking booking = bookingIn(BookingStatus.CONFIRMED);
        booking.setPeriod(new TimeRange(START, START.plusMinutes(40)));
        clock.set(START.plusMinutes(20));
        log.input("bookingId = {}", BOOKING_ID);

        service.markNoShow(BOOKING_ID);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_markNoShow_OneHourBookingBothRulesEqual_193() {
        // Khoảng giữa: đơn 60 phút, 50% = 30 phút = mức tối đa → 25 phút chưa đánh dấu, 30 phút 1 giây thì đánh dấu.
        Booking booking = bookingIn(BookingStatus.CONFIRMED);
        booking.setPeriod(new TimeRange(START, START.plusHours(1)));
        clock.set(START.plusMinutes(25));
        log.input("bookingId = {}", BOOKING_ID);

        service.markNoShow(BOOKING_ID);
        BookingStatus before = booking.getStatus();
        clock.set(START.plusMinutes(30).plusSeconds(1));
        service.markNoShow(BOOKING_ID);

        log.output("before = {}, after = {}", before, booking.getStatus());
        assertEquals(BookingStatus.CONFIRMED, before);
        assertEquals(BookingStatus.NO_SHOW, booking.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_markNoShow_AlreadyCheckedIn_194() {
        // Khách đã check-in (job đọc danh sách trước lúc check-in) → bỏ qua.
        Booking booking = bookingIn(BookingStatus.CHECKED_IN);
        clock.set(START.plusHours(1));
        log.input("bookingId = {}", BOOKING_ID);

        service.markNoShow(BOOKING_ID);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.CHECKED_IN, booking.getStatus());
        verify(bookings, never()).save(any());
    }

    // Viewpoint: VP7
    @Test
    public void test_markNoShow_ClearsPendingChange_195() {
        // Đơn còn ghi yêu cầu đổi chỗ (chưa bị job hủy) → xóa để nhả chỗ mới.
        Booking booking = bookingIn(BookingStatus.CONFIRMED);
        setPending(booking, 50_000, NOW.plusMinutes(10));
        clock.set(START.plusMinutes(31));
        log.input("bookingId = {}", BOOKING_ID);

        service.markNoShow(BOOKING_ID);

        log.output("status = {}, pendingResource = {}", booking.getStatus(), booking.getPendingResource());
        assertEquals(BookingStatus.NO_SHOW, booking.getStatus());
        assertNull(booking.getPendingResource());
    }

    // Viewpoint: VP7
    @Test
    public void test_markNoShow_BookingNotFound_196() {
        // Đơn không tồn tại.
        log.input("bookingId = {}", BOOKING_ID);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.markNoShow(BOOKING_ID));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // ---------- findNoShowIds ----------

    // Viewpoint: VP7
    @Test
    public void test_findNoShowIds_HasIds_197() {
        // Ứng viên: đơn CONFIRMED đã qua giờ bắt đầu, tính theo giờ hiện tại.
        when(bookings.findConfirmedIdsStartedBefore(NOW)).thenReturn(List.of(4L, 5L));
        log.input("now = {}", NOW);

        List<Long> ids = service.findNoShowIds();

        log.output("ids = {}", ids);
        assertEquals(List.of(4L, 5L), ids);
    }

    // Viewpoint: VP7
    @Test
    public void test_findNoShowIds_NoIds_198() {
        // Không có ứng viên.
        log.input("now = {}", NOW);

        List<Long> ids = service.findNoShowIds();

        log.output("ids = {}", ids);
        assertTrue(ids.isEmpty());
    }

    // ---------- warnEndingSoon ----------

    // Viewpoint: VP3
    @Test
    public void test_warnEndingSoon_CheckedIn_199() {
        // Đơn đang check-in → chuyển cho NotificationService xét mốc gửi.
        Booking booking = bookingIn(BookingStatus.CHECKED_IN);
        when(notifications.endWarning(booking)).thenReturn(true);
        log.input("bookingId = {}", BOOKING_ID);

        service.warnEndingSoon(BOOKING_ID);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.CHECKED_IN, booking.getStatus());
        verify(notifications).endWarning(booking);
    }

    // Viewpoint: VP3
    @Test
    public void test_warnEndingSoon_NotCheckedIn_200() {
        // Đơn đã chuyển OVERSTAY / check-out trước lượt quét → không cảnh báo.
        Booking booking = bookingIn(BookingStatus.OVERSTAY);
        log.input("bookingId = {}", BOOKING_ID);

        service.warnEndingSoon(BOOKING_ID);

        log.output("status = {}", booking.getStatus());
        assertEquals(BookingStatus.OVERSTAY, booking.getStatus());
        verify(notifications, never()).endWarning(any());
    }

    // Viewpoint: VP7
    @Test
    public void test_warnEndingSoon_BookingNotFound_201() {
        // Đơn không tồn tại.
        log.input("bookingId = {}", BOOKING_ID);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.warnEndingSoon(BOOKING_ID));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // ---------- findEndingSoonIds ----------

    // Viewpoint: VP7
    @Test
    public void test_findEndingSoonIds_HasIds_202() {
        // Ứng viên: đơn CHECKED_IN chưa tới giờ kết thúc, tính theo giờ hiện tại.
        when(bookings.findCheckedInIdsEndingAfter(NOW)).thenReturn(List.of(6L));
        log.input("now = {}", NOW);

        List<Long> ids = service.findEndingSoonIds();

        log.output("ids = {}", ids);
        assertEquals(List.of(6L), ids);
    }

    // Viewpoint: VP7
    @Test
    public void test_findEndingSoonIds_NoIds_203() {
        // Không có ứng viên.
        log.input("now = {}", NOW);

        List<Long> ids = service.findEndingSoonIds();

        log.output("ids = {}", ids);
        assertTrue(ids.isEmpty());
    }

    // ---------- dữ liệu dùng chung ----------

    private static PriceQuote quote(long total) {
        return new PriceQuote(2, 60, Money.vnd(total));
    }

    /** Đổi chỗ giữ nguyên vị trí. */
    private static RescheduleRequest change(LocalDateTime startAt, LocalDateTime endAt) {
        return new RescheduleRequest(null, startAt, endAt);
    }

    /** Yêu cầu đổi sang [NEW_START, NEW_END) tại vị trí hiện tại, chờ trả {@code amount}, hết hạn lúc {@code expiresAt}. */
    private void setPending(Booking booking, long amount, LocalDateTime expiresAt) {
        booking.setPendingResource(resource);
        booking.setPendingStartAt(NEW_START);
        booking.setPendingEndAt(NEW_END);
        booking.setPendingAmount(amount);
        booking.setPendingExpiresAt(expiresAt);
    }

    /** Vị trí khác cùng chi nhánh, tìm được bằng {@link #OTHER_RESOURCE_ID}. */
    private SpaceResource otherResource(ResourceTypeCode type) {
        SpaceResource other = new SpaceResource("MR-01", "Phòng họp 1", new ResourceType(type, "Phòng họp", "Phòng"), 6);
        other.setId(OTHER_RESOURCE_ID);
        other.setZone(resource.getZone());
        when(resources.findByIdForUpdate(OTHER_RESOURCE_ID)).thenReturn(Optional.of(other));
        return other;
    }

    private static HoldBookingRequest request(String voucherCode, boolean useMembership, List<AddonSelection> selections) {
        return new HoldBookingRequest(RESOURCE_ID, START, END, 2, CONTACT, selections, voucherCode, useMembership);
    }

    private AddonLine addonLine(long amount) {
        return new AddonLine(new Addon(branch, "A1", "Màn hình", 5, 10_000), 2, 10_000, Money.vnd(amount));
    }

    /** Đơn của {@link #CUSTOMER_ID} tại {@link #resource}, chưa thanh toán, có một dòng tiền thuê; tìm được theo id và QR. */
    private Booking bookingIn(BookingStatus status) {
        Booking booking = newBooking(status);
        booking.setQrToken(QR_TOKEN);
        when(bookings.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookings.findByQrToken(QR_TOKEN)).thenReturn(Optional.of(booking));
        return booking;
    }

    private Booking paidBookingIn(BookingStatus status) {
        Booking booking = bookingIn(status);
        booking.setPaidAmount(RENTAL);
        return booking;
    }

    /** Đơn CONFIRMED, trong khung giờ của {@link #START}, tìm được bằng mã QR cho trước. */
    private Booking bookingWithToken(String qrToken) {
        Booking booking = newBooking(BookingStatus.CONFIRMED);
        booking.setQrToken(qrToken);
        when(bookings.findByQrToken(qrToken)).thenReturn(Optional.of(booking));
        return booking;
    }

    private Booking checkedOutWithDebt(long debt) {
        Booking booking = paidBookingIn(BookingStatus.CHECKED_OUT);
        addCharge(booking, ChargeType.EXTENSION, debt);
        return booking;
    }

    private Booking newBooking(BookingStatus status) {
        Booking booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setCustomer(customer);
        booking.setResource(resource);
        booking.setPeriod(new TimeRange(START, END));
        booking.setPartySize(2);
        booking.setStatus(status);
        addCharge(booking, ChargeType.RENTAL, RENTAL);
        return booking;
    }

    private static void addCharge(Booking booking, ChargeType type, long amount) {
        booking.getCharges().add(BookingCharge.of(booking, type, Money.vnd(amount), booking.getPeriod(), NOW));
    }

    @SuppressWarnings("unchecked")
    private Page<Booking> stubFindAll() {
        Page<Booking> page = new PageImpl<>(List.of());
        doReturn(page).when(bookings).findAll(any(Specification.class), any(Pageable.class));
        return page;
    }

    private Pageable capturedPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(bookings).findAll(org.mockito.ArgumentMatchers.<Specification<Booking>>any(), captor.capture());
        return captor.getValue();
    }

    /** Chạy Specification mà service đã truyền cho repository trên CriteriaBuilder giả lập. */
    @SuppressWarnings("unchecked")
    private CriteriaBuilder runSpec() {
        ArgumentCaptor<Specification<Booking>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(bookings).findAll(captor.capture(), any(Pageable.class));
        Root<Booking> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        captor.getValue().toPredicate(root, null, cb);
        return cb;
    }

    /** Số điều kiện được nối bằng {@code cb.and(...)}. */
    private static int predicateCount(CriteriaBuilder cb) {
        ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);
        verify(cb).and(captor.capture());
        return captor.getValue().length;
    }
}
