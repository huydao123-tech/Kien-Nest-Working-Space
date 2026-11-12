package com.cowork.business.service;

import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.Payment;
import com.cowork.dataaccess.entity.Refund;
import com.cowork.dataaccess.enums.PaymentStatus;
import com.cowork.dataaccess.enums.RefundStatus;
import com.cowork.dataaccess.repository.PaymentRepository;
import com.cowork.dataaccess.repository.RefundRepository;
import com.cowork.dataaccess.valueobject.Money;
import com.cowork.dto.RefundDtos.RefundResponse;
import com.cowork.support.MutableClock;
import com.cowork.support.TestCaseLogRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UC06, UC25, FR-NOTI-04: ghi yêu cầu hoàn tiền vào giao dịch cổng thành công gần nhất, xác nhận đã hoàn xong. Viết theo {@code .claude/rules/unit-test.md}. */
public class RefundServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 8, 0);
    private static final Long BOOKING_ID = 100L;
    private static final String REASON = "CUSTOMER_CANCELLED";

    @Rule
    public final TestCaseLogRule log = new TestCaseLogRule();

    private PaymentRepository payments;
    private RefundRepository refunds;
    private NotificationService notifications;
    private RefundService service;
    private Booking booking;

    @Before
    public void setUp() {
        payments = mock(PaymentRepository.class);
        refunds = mock(RefundRepository.class);
        notifications = mock(NotificationService.class);
        MutableClock clock = new MutableClock(ZoneId.of("Asia/Ho_Chi_Minh"));
        clock.set(NOW);
        service = new RefundService(payments, refunds, notifications, clock);
        booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setCode("BKTEST0001");
        when(refunds.save(any(Refund.class))).thenAnswer(call -> call.getArgument(0));
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_request_ZeroAmount_001() {
        // Cận dưới: số tiền 0 → không ghi gì, không đọc giao dịch.
        Money amount = Money.ZERO;
        log.input("booking = {}, amount = {}, reason = {}, now = {}", BOOKING_ID, amount, REASON, NOW);

        Optional<Refund> result = service.request(booking, amount, REASON, NOW);

        log.output("present = {}", result.isPresent());
        assertFalse(result.isPresent());
        verify(payments, never()).findByBookingId(anyLong());
    }

    // Viewpoint: VP4
    @Test
    public void test_request_NoPayments_002() {
        // Vòng lặp 0 record: đơn thu tiền ngoài cổng → không ghi refund.
        when(payments.findByBookingId(BOOKING_ID)).thenReturn(List.of());
        Money amount = Money.vnd(1);
        log.input("booking = {}, amount = {}, reason = {}, now = {}", BOOKING_ID, amount, REASON, NOW);

        Optional<Refund> result = service.request(booking, amount, REASON, NOW);

        log.output("present = {}", result.isPresent());
        assertFalse(result.isPresent());
        verify(refunds, never()).save(any());
    }

    // Viewpoint: VP3, VP4
    @Test
    public void test_request_OnlyFailedPayment_003() {
        // Vòng lặp 1 record nhưng giao dịch thất bại: không có chỗ ghi refund.
        when(payments.findByBookingId(BOOKING_ID)).thenReturn(List.of(payment(1L, 100_000, PaymentStatus.FAILED)));
        Money amount = Money.vnd(100_000);
        log.input("booking = {}, amount = {}, reason = {}, now = {}", BOOKING_ID, amount, REASON, NOW);

        Optional<Refund> result = service.request(booking, amount, REASON, NOW);

        log.output("present = {}", result.isPresent());
        assertFalse(result.isPresent());
        verify(refunds, never()).save(any());
    }

    // Viewpoint: VP4
    @Test
    public void test_request_LatestSucceededPayment_004() {
        // Nhiều giao dịch: chọn giao dịch thành công có id lớn nhất; trạng thái REQUESTED, ghi lý do và thời điểm.
        Payment older = payment(1L, 100_000, PaymentStatus.SUCCEEDED);
        Payment latest = payment(3L, 50_000, PaymentStatus.SUCCEEDED);
        when(payments.findByBookingId(BOOKING_ID)).thenReturn(List.of(older, latest, payment(4L, 70_000, PaymentStatus.FAILED)));
        Money amount = Money.vnd(30_000);
        log.input("booking = {}, amount = {}, reason = {}, now = {}", BOOKING_ID, amount, REASON, NOW);

        Refund refund = service.request(booking, amount, REASON, NOW).orElseThrow();

        log.output("payment = {}, amount = {}, reason = {}, status = {}, requestedAt = {}", refund.getPayment().getId(),
                refund.getAmount(), refund.getReason(), refund.getStatus(), refund.getRequestedAt());
        assertSame(latest, refund.getPayment());
        assertEquals(30_000L, refund.getAmount());
        assertEquals(REASON, refund.getReason());
        assertEquals(RefundStatus.REQUESTED, refund.getStatus());
        assertEquals(NOW, refund.getRequestedAt());
    }

    // Viewpoint: VP3
    @Test
    public void test_request_AmountEqualsPayment_005() {
        // Cận: hoàn đúng bằng số của giao dịch.
        when(payments.findByBookingId(BOOKING_ID)).thenReturn(List.of(payment(1L, 100_000, PaymentStatus.SUCCEEDED)));
        Money amount = Money.vnd(100_000);
        log.input("booking = {}, amount = {}, reason = {}, now = {}", BOOKING_ID, amount, REASON, NOW);

        Refund refund = service.request(booking, amount, REASON, NOW).orElseThrow();

        log.output("amount = {}", refund.getAmount());
        assertEquals(100_000L, refund.getAmount());
    }

    // Viewpoint: VP3
    @Test
    public void test_request_AmountOverPayment_006() {
        // Hoàn vượt số của giao dịch 1 đồng: chỉ ghi tối đa số của giao dịch.
        when(payments.findByBookingId(BOOKING_ID)).thenReturn(List.of(payment(1L, 100_000, PaymentStatus.SUCCEEDED)));
        Money amount = Money.vnd(100_001);
        log.input("booking = {}, amount = {}, reason = {}, now = {}", BOOKING_ID, amount, REASON, NOW);

        Refund refund = service.request(booking, amount, REASON, NOW).orElseThrow();

        log.output("amount = {}", refund.getAmount());
        assertEquals(100_000L, refund.getAmount());
    }

    // Viewpoint: VP7
    @Test
    public void test_request_NullAmount_007() {
        // Số tiền null.
        Money amount = null;
        log.input("booking = {}, amount = {}, reason = {}, now = {}", BOOKING_ID, amount, REASON, NOW);

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.request(booking, amount, REASON, NOW));

        log.output("exception = {}", thrown.getClass().getSimpleName());
        assertEquals(NullPointerException.class, thrown.getClass());
    }

    // ---------- complete ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_complete_Requested_008() {
        // Yêu cầu đang chờ hoàn: chuyển REFUNDED, ghi giờ xong, nhắn khách đúng số tiền.
        Refund refund = refundIn(RefundStatus.REQUESTED, 140_000);
        Long refundId = refund.getId();
        log.input("refundId = {}", refundId);

        RefundResponse response = service.complete(refundId);

        log.output("status = {}, completedAt = {}, bookingCode = {}", refund.getStatus(), refund.getCompletedAt(), response.bookingCode());
        assertEquals(RefundStatus.REFUNDED, refund.getStatus());
        assertEquals(NOW, refund.getCompletedAt());
        assertEquals("BKTEST0001", response.bookingCode());
        verify(notifications).refundCompleted(booking, Money.vnd(140_000));
    }

    // Viewpoint: VP3
    @Test
    public void test_complete_AlreadyRefunded_009() {
        // Bấm "đã hoàn" lần hai: lỗi trạng thái, không nhắn lại.
        Refund refund = refundIn(RefundStatus.REFUNDED, 140_000);
        Long refundId = refund.getId();
        log.input("refundId = {}", refundId);

        DomainException thrown = assertThrows(DomainException.class, () -> service.complete(refundId));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_REFUND_STATE", thrown.code());
        verify(refunds, never()).save(any());
        verify(notifications, never()).refundCompleted(any(), any());
    }

    // Viewpoint: VP3
    @Test
    public void test_complete_Failed_010() {
        // Yêu cầu đã thất bại: không xác nhận được.
        Refund refund = refundIn(RefundStatus.FAILED, 140_000);
        Long refundId = refund.getId();
        log.input("refundId = {}", refundId);

        DomainException thrown = assertThrows(DomainException.class, () -> service.complete(refundId));

        log.output("code = {}, status = {}", thrown.code(), refund.getStatus());
        assertEquals("INVALID_REFUND_STATE", thrown.code());
        assertEquals(RefundStatus.FAILED, refund.getStatus());
    }

    // Viewpoint: VP7
    @Test
    public void test_complete_NotFound_011() {
        // Không có yêu cầu hoàn.
        Long refundId = 99L;
        log.input("refundId = {}", refundId);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.complete(refundId));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
        verify(notifications, never()).refundCompleted(any(), any());
    }

    // ---------- list ----------

    // Viewpoint: VP4
    @Test
    public void test_list_ManyRefunds_012() {
        // Vòng lặp > 1 record: chuyển từng dòng sang DTO, giữ thứ tự.
        RefundStatus status = RefundStatus.REQUESTED;
        when(refunds.findWithBookingByStatus(status)).thenReturn(List.of(refund(1L, 100_000), refund(2L, 50_000)));
        log.input("status = {}", status);

        List<RefundResponse> result = service.list(status);

        log.output("ids = {}, bookingId = {}", result.stream().map(RefundResponse::id).toList(), result.get(0).bookingId());
        assertEquals(List.of(1L, 2L), result.stream().map(RefundResponse::id).toList());
        assertEquals(BOOKING_ID, result.get(0).bookingId());
    }

    // Viewpoint: VP4
    @Test
    public void test_list_OneRefund_013() {
        // Vòng lặp 1 record.
        RefundStatus status = RefundStatus.REFUNDED;
        when(refunds.findWithBookingByStatus(status)).thenReturn(List.of(refund(5L, 70_000)));
        log.input("status = {}", status);

        List<RefundResponse> result = service.list(status);

        log.output("size = {}, amount = {}", result.size(), result.get(0).amount());
        assertEquals(1, result.size());
        assertEquals(70_000L, result.get(0).amount());
    }

    // Viewpoint: VP4
    @Test
    public void test_list_NoRefunds_014() {
        // Vòng lặp 0 record.
        RefundStatus status = RefundStatus.REQUESTED;
        log.input("status = {}", status);

        List<RefundResponse> result = service.list(status);

        log.output("size = {}", result.size());
        assertTrue(result.isEmpty());
    }

    /** Yêu cầu hoàn trên giao dịch thành công của {@link #booking}, tìm được bằng {@code findByIdForUpdate}. */
    private Refund refundIn(RefundStatus status, long amount) {
        Refund r = refund(20L, amount);
        r.setStatus(status);
        when(refunds.findByIdForUpdate(20L)).thenReturn(Optional.of(r));
        return r;
    }

    private Refund refund(Long id, long amount) {
        Refund r = new Refund(payment(1L, amount, PaymentStatus.SUCCEEDED), amount, REASON);
        r.setId(id);
        r.setRequestedAt(NOW);
        return r;
    }

    private Payment payment(Long id, long amount, PaymentStatus status) {
        Payment p = new Payment();
        p.setId(id);
        p.setBooking(booking);
        p.setAmount(amount);
        p.setStatus(status);
        return p;
    }
}
