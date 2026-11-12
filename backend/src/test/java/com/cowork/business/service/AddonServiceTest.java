package com.cowork.business.service;

import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.business.service.AddonService.AddonLine;
import com.cowork.dataaccess.entity.Addon;
import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.BookingAddon;
import com.cowork.dataaccess.entity.Branch;
import com.cowork.dataaccess.enums.AddonStatus;
import com.cowork.dataaccess.enums.BookingStatus;
import com.cowork.dataaccess.repository.AddonRepository;
import com.cowork.dataaccess.repository.BookingAddonRepository;
import com.cowork.dataaccess.repository.BookingAddonRepository.Reservation;
import com.cowork.dataaccess.repository.BranchRepository;
import com.cowork.dataaccess.valueobject.Money;
import com.cowork.dataaccess.valueobject.TimeRange;
import com.cowork.dto.BookingDtos.AddonSelection;
import com.cowork.dto.CommerceDtos.AddonDetail;
import com.cowork.dto.CommerceDtos.AddonRequest;
import com.cowork.support.TestCaseLogRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FR-BOOK-02, FR-ADM-07: add-on thuê kèm — tồn kho theo khung giờ, tiền theo block, quản trị.
 * Viết theo {@code .claude/rules/unit-test.md}. Khung giờ test: 09:00–11:00, 4 block.
 */
public class AddonServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 3, 9, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 3, 11, 0);
    private static final TimeRange PERIOD = new TimeRange(START, END);
    private static final int BLOCKS = 4;
    private static final Long PROJECTOR_ID = 1L;
    private static final Long MONITOR_ID = 2L;
    private static final Long BOOKING_ID = 100L;

    @Rule
    public final TestCaseLogRule log = new TestCaseLogRule();

    private AddonRepository addons;
    private BookingAddonRepository bookingAddons;
    private BranchRepository branches;
    private AddonService service;
    private Branch branch;
    private Addon projector;
    private Addon monitor;

    @Before
    public void setUp() {
        addons = mock(AddonRepository.class);
        bookingAddons = mock(BookingAddonRepository.class);
        branches = mock(BranchRepository.class);
        service = new AddonService(addons, bookingAddons, branches);
        branch = branch(1L);
        projector = addon(PROJECTOR_ID, branch, "PROJ", 2, 20_000);
        monitor = addon(MONITOR_ID, branch, "MON", 5, 10_000);
        when(bookingAddons.findReservations(anyLong(), any(), any(), any(), anyLong())).thenReturn(List.of());
        when(bookingAddons.findPendingReservations(anyLong(), any(), any(), anyLong())).thenReturn(List.of());
        when(branches.findById(1L)).thenReturn(Optional.of(branch));
        when(addons.save(any(Addon.class))).thenAnswer(call -> call.getArgument(0));
    }

    // ---------- reserve ----------

    // Viewpoint: VP7, VP4
    @Test
    public void test_reserve_NullSelections_001() {
        // Không chọn add-on (null).
        log.input("branch = {}, period = {}, blocks = {}, selections = {}", branch.getId(), PERIOD, BLOCKS, null);

        List<AddonLine> lines = service.reserve(branch, PERIOD, BLOCKS, null);

        log.output("lines = {}", lines.size());
        assertEquals(0, lines.size());
        verify(addons, never()).findByIdForUpdate(anyLong());
    }

    // Viewpoint: VP4
    @Test
    public void test_reserve_EmptySelections_002() {
        // Danh sách rỗng (0 record).
        List<AddonSelection> selections = List.of();
        log.input("branch = {}, period = {}, blocks = {}, selections = {}", branch.getId(), PERIOD, BLOCKS, selections);

        List<AddonLine> lines = service.reserve(branch, PERIOD, BLOCKS, selections);

        log.output("lines = {}", lines.size());
        assertEquals(0, lines.size());
    }

    // Viewpoint: VP4
    @Test
    public void test_reserve_OneAddon_003() {
        // 1 add-on: tiền = 20.000 × 4 block × 2 cái.
        List<AddonSelection> selections = List.of(new AddonSelection(PROJECTOR_ID, 2));
        log.input("branch = {}, period = {}, blocks = {}, selections = {}", branch.getId(), PERIOD, BLOCKS, selections);

        List<AddonLine> lines = service.reserve(branch, PERIOD, BLOCKS, selections);

        AddonLine line = lines.get(0);
        log.output("addon = {}, quantity = {}, unitPrice = {}, amount = {}", line.addon().getCode(), line.quantity(),
                line.unitPrice(), line.amount());
        assertSame(projector, line.addon());
        assertEquals(2, line.quantity());
        assertEquals(20_000L, line.unitPrice());
        assertEquals(Money.vnd(160_000), line.amount());
    }

    // Viewpoint: VP4
    @Test
    public void test_reserve_ManyAddonsLockedByIdOrder_004() {
        // Nhiều add-on: khóa theo id tăng dần dù khách chọn theo thứ tự ngược.
        List<AddonSelection> selections = List.of(new AddonSelection(MONITOR_ID, 1), new AddonSelection(PROJECTOR_ID, 1));
        log.input("branch = {}, period = {}, blocks = {}, selections = {}", branch.getId(), PERIOD, BLOCKS, selections);

        List<AddonLine> lines = service.reserve(branch, PERIOD, BLOCKS, selections);

        log.output("first = {}, second = {}", lines.get(0).addon().getCode(), lines.get(1).addon().getCode());
        assertEquals("PROJ", lines.get(0).addon().getCode());
        assertEquals("MON", lines.get(1).addon().getCode());
        InOrder order = inOrder(addons);
        order.verify(addons).findByIdForUpdate(PROJECTOR_ID);
        order.verify(addons).findByIdForUpdate(MONITOR_ID);
    }

    // Viewpoint: VP3
    @Test
    public void test_reserve_DuplicateAddon_005() {
        // Chọn lặp cùng add-on.
        List<AddonSelection> selections = List.of(new AddonSelection(PROJECTOR_ID, 1), new AddonSelection(PROJECTOR_ID, 1));
        log.input("branch = {}, period = {}, blocks = {}, selections = {}", branch.getId(), PERIOD, BLOCKS, selections);

        DomainException thrown = assertThrows(DomainException.class, () -> service.reserve(branch, PERIOD, BLOCKS, selections));

        log.output("code = {}", thrown.code());
        assertEquals("DUPLICATE_ADDON", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_reserve_AddonNotFound_006() {
        // Add-on không tồn tại.
        List<AddonSelection> selections = List.of(new AddonSelection(99L, 1));
        log.input("branch = {}, period = {}, blocks = {}, selections = {}", branch.getId(), PERIOD, BLOCKS, selections);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.reserve(branch, PERIOD, BLOCKS, selections));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_reserve_InactiveAddon_007() {
        // Add-on ngưng cho thuê.
        projector.setStatus(AddonStatus.INACTIVE);
        List<AddonSelection> selections = List.of(new AddonSelection(PROJECTOR_ID, 1));
        log.input("branch = {}, period = {}, blocks = {}, selections = {}", branch.getId(), PERIOD, BLOCKS, selections);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.reserve(branch, PERIOD, BLOCKS, selections));

        log.output("code = {}", thrown.code());
        assertEquals("ADDON_NOT_AVAILABLE", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_reserve_OtherBranch_008() {
        // Add-on của chi nhánh khác.
        List<AddonSelection> selections = List.of(new AddonSelection(PROJECTOR_ID, 1));
        Branch other = branch(2L);
        log.input("branch = {}, period = {}, blocks = {}, selections = {}", other.getId(), PERIOD, BLOCKS, selections);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.reserve(other, PERIOD, BLOCKS, selections));

        log.output("code = {}", thrown.code());
        assertEquals("ADDON_NOT_AVAILABLE", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_reserve_NullBranch_009() {
        // Vị trí chưa thuộc chi nhánh nào.
        List<AddonSelection> selections = List.of(new AddonSelection(PROJECTOR_ID, 1));
        log.input("branch = {}, period = {}, blocks = {}, selections = {}", null, PERIOD, BLOCKS, selections);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.reserve(null, PERIOD, BLOCKS, selections));

        log.output("code = {}", thrown.code());
        assertEquals("ADDON_NOT_AVAILABLE", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_reserve_QuantityEqualsAvailable_010() {
        // Cận trên: tồn kho 2, đơn khác đang giữ 1 → còn đúng 1.
        when(bookingAddons.findReservations(eq(PROJECTOR_ID), eq(START), eq(END), eq(BookingStatus.SLOT_OCCUPYING), eq(0L)))
                .thenReturn(List.of(reservation(START, END, 1)));
        List<AddonSelection> selections = List.of(new AddonSelection(PROJECTOR_ID, 1));
        log.input("branch = {}, period = {}, blocks = {}, selections = {}", branch.getId(), PERIOD, BLOCKS, selections);

        List<AddonLine> lines = service.reserve(branch, PERIOD, BLOCKS, selections);

        log.output("lines = {}", lines.size());
        assertEquals(1, lines.size());
    }

    // Viewpoint: VP3
    @Test
    public void test_reserve_QuantityOverAvailable_011() {
        // Vượt 1: tồn kho 2, đang giữ 1, xin 2.
        when(bookingAddons.findReservations(eq(PROJECTOR_ID), any(), any(), any(), anyLong()))
                .thenReturn(List.of(reservation(START, END, 1)));
        List<AddonSelection> selections = List.of(new AddonSelection(PROJECTOR_ID, 2));
        log.input("branch = {}, period = {}, blocks = {}, selections = {}", branch.getId(), PERIOD, BLOCKS, selections);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.reserve(branch, PERIOD, BLOCKS, selections));

        log.output("code = {}", thrown.code());
        assertEquals("ADDON_OUT_OF_STOCK", thrown.code());
    }

    // Viewpoint: VP8
    @Test
    public void test_reserve_PendingChangeCounted_012() {
        // Add-on của yêu cầu đổi chỗ đang chờ thanh toán cũng chiếm tồn kho (booking.md Prompt 2).
        when(bookingAddons.findPendingReservations(eq(PROJECTOR_ID), any(), any(), anyLong()))
                .thenReturn(List.of(reservation(START, END, 2)));
        List<AddonSelection> selections = List.of(new AddonSelection(PROJECTOR_ID, 1));
        log.input("branch = {}, period = {}, blocks = {}, selections = {}", branch.getId(), PERIOD, BLOCKS, selections);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.reserve(branch, PERIOD, BLOCKS, selections));

        log.output("code = {}", thrown.code());
        assertEquals("ADDON_OUT_OF_STOCK", thrown.code());
    }

    // ---------- recheck ----------

    // Viewpoint: VP4
    @Test
    public void test_recheck_NoAddons_013() {
        // Đơn không thuê add-on (0 record): không kiểm tra gì.
        Booking booking = booking();
        log.input("booking = {}, newBranch = {}, newPeriod = {}", BOOKING_ID, branch.getId(), PERIOD);

        service.recheck(booking, branch, PERIOD);

        log.output("addons = {}", booking.getAddons().size());
        assertEquals(0, booking.getAddons().size());
        verify(addons, never()).findByIdForUpdate(anyLong());
    }

    // Viewpoint: VP3, VP4
    @Test
    public void test_recheck_StockOkExcludingOwnBooking_014() {
        // Còn đủ tồn kho; khi đếm thì bỏ chính đơn đang đổi.
        Booking booking = booking();
        booking.getAddons().add(new BookingAddon(booking, projector, 2, 20_000));
        log.input("booking = {}, newBranch = {}, newPeriod = {}", BOOKING_ID, branch.getId(), PERIOD);

        service.recheck(booking, branch, PERIOD);

        log.output("checkedAddon = {}", projector.getCode());
        assertEquals("PROJ", projector.getCode());
        verify(bookingAddons).findReservations(PROJECTOR_ID, START, END, BookingStatus.SLOT_OCCUPYING, BOOKING_ID);
        verify(bookingAddons).findPendingReservations(PROJECTOR_ID, START, END, BOOKING_ID);
    }

    // Viewpoint: VP3
    @Test
    public void test_recheck_OutOfStock_015() {
        // Khung mới không còn đủ.
        Booking booking = booking();
        booking.getAddons().add(new BookingAddon(booking, projector, 2, 20_000));
        when(bookingAddons.findReservations(eq(PROJECTOR_ID), any(), any(), any(), anyLong()))
                .thenReturn(List.of(reservation(START, END, 1)));
        log.input("booking = {}, newBranch = {}, newPeriod = {}", BOOKING_ID, branch.getId(), PERIOD);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.recheck(booking, branch, PERIOD));

        log.output("code = {}", thrown.code());
        assertEquals("ADDON_OUT_OF_STOCK", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_recheck_OtherBranch_016() {
        // Đổi sang vị trí ở chi nhánh khác khi đơn có add-on.
        Booking booking = booking();
        booking.getAddons().add(new BookingAddon(booking, projector, 1, 20_000));
        Branch other = branch(2L);
        log.input("booking = {}, newBranch = {}, newPeriod = {}", BOOKING_ID, other.getId(), PERIOD);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.recheck(booking, other, PERIOD));

        log.output("code = {}", thrown.code());
        assertEquals("ADDON_NOT_AVAILABLE", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_recheck_NullBranchWithAddons_017() {
        // Vị trí mới chưa thuộc chi nhánh nào.
        Booking booking = booking();
        booking.getAddons().add(new BookingAddon(booking, projector, 1, 20_000));
        log.input("booking = {}, newBranch = {}, newPeriod = {}", BOOKING_ID, null, PERIOD);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.recheck(booking, null, PERIOD));

        log.output("code = {}", thrown.code());
        assertEquals("ADDON_NOT_AVAILABLE", thrown.code());
    }

    // ---------- peakUsage ----------

    // Viewpoint: VP4
    @Test
    public void test_peakUsage_NoReservations_018() {
        // 0 record.
        List<Reservation> reservations = List.of();
        log.input("reservations = {}, window = {}", reservations.size(), PERIOD);

        int peak = AddonService.peakUsage(reservations, PERIOD);

        log.output("peak = {}", peak);
        assertEquals(0, peak);
    }

    // Viewpoint: VP3, VP4
    @Test
    public void test_peakUsage_TouchingNotSummed_019() {
        // Hai đơn chạm mép 10:00 (nửa mở): không cộng dồn.
        List<Reservation> reservations = List.of(reservation(START, START.plusHours(1), 2),
                reservation(START.plusHours(1), END, 3));
        log.input("reservations = {}, window = {}", reservations.size(), PERIOD);

        int peak = AddonService.peakUsage(reservations, PERIOD);

        log.output("peak = {}", peak);
        assertEquals(3, peak);
    }

    // Viewpoint: VP3, VP4
    @Test
    public void test_peakUsage_OverlappingSummed_020() {
        // Hai đơn chồng nhau 09:30–10:00: cộng dồn.
        List<Reservation> reservations = List.of(reservation(START, START.plusHours(1), 2),
                reservation(START.plusMinutes(30), END, 3));
        log.input("reservations = {}, window = {}", reservations.size(), PERIOD);

        int peak = AddonService.peakUsage(reservations, PERIOD);

        log.output("peak = {}", peak);
        assertEquals(5, peak);
    }

    // Viewpoint: VP3
    @Test
    public void test_peakUsage_OutsideWindowIgnored_021() {
        // Đơn kết thúc đúng lúc khung bắt đầu: không tính; đơn vắt qua đầu khung được cắt vào khung.
        List<Reservation> reservations = List.of(reservation(START.minusHours(2), START, 4),
                reservation(START.minusHours(1), START.plusMinutes(30), 1));
        log.input("reservations = {}, window = {}", reservations.size(), PERIOD);

        int peak = AddonService.peakUsage(reservations, PERIOD);

        log.output("peak = {}", peak);
        assertEquals(1, peak);
    }

    // ---------- quản trị ----------

    // Viewpoint: VP7
    @Test
    public void test_get_NotFound_022() {
        // Không có add-on.
        log.input("id = {}", 99L);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.get(99L));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP2, VP3
    @Test
    public void test_create_NewCode_023() {
        // Mã mới: viết hoa, cắt khoảng trắng, mặc định ACTIVE.
        AddonRequest request = new AddonRequest(1L, " spk ", " Loa ", 3, 15_000, null);
        log.input("request = {}", request);

        AddonDetail detail = service.create(request);

        log.output("code = {}, name = {}, status = {}, branchId = {}", detail.code(), detail.name(), detail.status(), detail.branchId());
        assertEquals("SPK", detail.code());
        assertEquals("Loa", detail.name());
        assertEquals(AddonStatus.ACTIVE, detail.status());
        assertEquals(Long.valueOf(1L), detail.branchId());
    }

    // Viewpoint: VP3
    @Test
    public void test_create_CodeExists_024() {
        // Chi nhánh đã có mã này.
        when(addons.existsByBranchIdAndCodeIgnoreCase(1L, "PROJ")).thenReturn(true);
        AddonRequest request = new AddonRequest(1L, "proj", "Máy chiếu", 1, 1, null);
        log.input("request = {}", request);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.create(request));

        log.output("code = {}", thrown.code());
        assertEquals("CODE_ALREADY_EXISTS", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_create_BranchNotFound_025() {
        // Chi nhánh không tồn tại.
        AddonRequest request = new AddonRequest(99L, "SPK", "Loa", 1, 1, null);
        log.input("request = {}", request);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.create(request));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP3, VP5
    @Test
    public void test_update_SameCodeSkipsCheck_026() {
        // Giữ mã và chi nhánh: không kiểm tra trùng; sửa tồn kho, trạng thái.
        when(addons.findById(PROJECTOR_ID)).thenReturn(Optional.of(projector));
        AddonRequest request = new AddonRequest(1L, "proj", "Máy chiếu", 0, 20_000, AddonStatus.INACTIVE);
        log.input("id = {}, request = {}", PROJECTOR_ID, request);

        AddonDetail detail = service.update(PROJECTOR_ID, request);

        log.output("stock = {}, status = {}", detail.stock(), detail.status());
        assertEquals(0, detail.stock());
        assertEquals(AddonStatus.INACTIVE, detail.status());
        verify(addons, never()).existsByBranchIdAndCodeIgnoreCase(anyLong(), any());
    }

    // Viewpoint: VP3
    @Test
    public void test_update_CodeTakenByOther_027() {
        // Đổi sang mã đã có trong chi nhánh.
        when(addons.findById(PROJECTOR_ID)).thenReturn(Optional.of(projector));
        when(addons.existsByBranchIdAndCodeIgnoreCase(1L, "MON")).thenReturn(true);
        AddonRequest request = new AddonRequest(1L, "MON", "Máy chiếu", 2, 20_000, null);
        log.input("id = {}, request = {}", PROJECTOR_ID, request);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.update(PROJECTOR_ID, request));

        log.output("code = {}", thrown.code());
        assertEquals("CODE_ALREADY_EXISTS", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_update_MoveToOtherBranch_028() {
        // Cùng mã nhưng chuyển chi nhánh: phải kiểm tra trùng ở chi nhánh mới.
        when(addons.findById(PROJECTOR_ID)).thenReturn(Optional.of(projector));
        Branch other = branch(2L);
        when(branches.findById(2L)).thenReturn(Optional.of(other));
        AddonRequest request = new AddonRequest(2L, "PROJ", "Máy chiếu", 2, 20_000, null);
        log.input("id = {}, request = {}", PROJECTOR_ID, request);

        AddonDetail detail = service.update(PROJECTOR_ID, request);

        log.output("branchId = {}", detail.branchId());
        assertEquals(Long.valueOf(2L), detail.branchId());
        verify(addons).existsByBranchIdAndCodeIgnoreCase(2L, "PROJ");
    }

    // Viewpoint: VP7
    @Test
    public void test_delete_Found_029() {
        // Xóa và flush ngay.
        when(addons.findById(PROJECTOR_ID)).thenReturn(Optional.of(projector));
        log.input("id = {}", PROJECTOR_ID);

        service.delete(PROJECTOR_ID);

        log.output("deleted = {}", projector.getCode());
        assertTrue(projector.getCode().equals("PROJ"));
        verify(addons).delete(projector);
        verify(addons).flush();
    }

    private static Branch branch(Long id) {
        Branch b = new Branch("B" + id, "Chi nhánh " + id, "Địa chỉ", LocalTime.of(7, 0), LocalTime.of(22, 0));
        b.setId(id);
        return b;
    }

    private Addon addon(Long id, Branch owner, String code, int stock, long price) {
        Addon a = new Addon(owner, code, code, stock, price);
        a.setId(id);
        when(addons.findByIdForUpdate(id)).thenReturn(Optional.of(a));
        return a;
    }

    private static Booking booking() {
        Booking b = new Booking();
        b.setId(BOOKING_ID);
        return b;
    }

    private static Reservation reservation(LocalDateTime start, LocalDateTime end, int quantity) {
        return new Reservation() {
            @Override
            public LocalDateTime getStartAt() {
                return start;
            }

            @Override
            public LocalDateTime getEndAt() {
                return end;
            }

            @Override
            public int getQuantity() {
                return quantity;
            }
        };
    }
}
