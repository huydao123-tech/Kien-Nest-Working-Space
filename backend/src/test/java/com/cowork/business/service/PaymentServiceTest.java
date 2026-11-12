package com.cowork.business.service;

import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.BookingCharge;
import com.cowork.dataaccess.entity.Payment;
import com.cowork.dataaccess.enums.BookingStatus;
import com.cowork.dataaccess.enums.ChargeType;
import com.cowork.dataaccess.enums.PaymentMethod;
import com.cowork.dataaccess.enums.PaymentStatus;
import com.cowork.dataaccess.integration.PaymentGateway;
import com.cowork.dataaccess.integration.PaymentGateway.CheckoutSession;
import com.cowork.dataaccess.repository.PaymentRepository;
import com.cowork.dataaccess.valueobject.Money;
import com.cowork.dataaccess.valueobject.TimeRange;
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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC04, booking.md Prompt 2: tạo giao dịch thanh toán (đơn giữ chỗ hoặc phần chênh đổi chỗ) và xử lý kết quả từ cổng.
 * Viết theo {@code .claude/rules/unit-test.md}. Cổng thanh toán và BookingService được giả lập.
 */
public class PaymentServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 8, 0);
    private static final Long BOOKING_ID = 100L;
    private static final String TXN = "txn-1";
    private static final long TOTAL = 200_000L;

    @Rule
    public final TestCaseLogRule log = new TestCaseLogRule();

    private PaymentRepository payments;
    private PaymentGateway gateway;
    private BookingService bookingService;
    private PaymentService service;
    private Booking booking;

    @Before
    public void setUp() {
        MutableClock clock = new MutableClock(ZoneId.of("Asia/Ho_Chi_Minh"));
        clock.set(NOW);
        payments = mock(PaymentRepository.class);
        gateway = mock(PaymentGateway.class);
        bookingService = mock(BookingService.class);
        service = new PaymentService(payments, gateway, bookingService, clock);

        booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setCode("BKTEST0001");
        booking.setPeriod(new TimeRange(NOW.plusDays(1), NOW.plusDays(1).plusHours(2)));
        booking.setHoldExpiresAt(NOW.plusMinutes(10));
        booking.getCharges().add(BookingCharge.of(booking, ChargeType.RENTAL, Money.vnd(TOTAL), booking.getPeriod(), NOW));
        when(bookingService.get(BOOKING_ID)).thenReturn(booking);
        when(payments.findByBookingId(BOOKING_ID)).thenReturn(List.of());
        when(gateway.createCheckout(anyString(), any(), anyString(), any())).thenReturn(new CheckoutSession(TXN, "https://pay/1"));
        when(payments.save(any(Payment.class))).thenAnswer(call -> call.getArgument(0));
    }

    // ---------- initiate ----------

    // Viewpoint: VP3, VP4
    @Test
    public void test_initiate_HeldNoPayments_001() {
        // Đơn giữ chỗ, chưa có giao dịch (0 record): tạo giao dịch PENDING đúng số còn nợ, hạn theo giờ giữ chỗ.
        booking.setStatus(BookingStatus.HELD);
        log.input("bookingId = {}, method = {}", BOOKING_ID, PaymentMethod.VIETQR);

        Payment payment = service.initiate(BOOKING_ID, PaymentMethod.VIETQR);

        log.output("status = {}, amount = {}, gatewayTransactionId = {}, checkoutUrl = {}, keyPrefix = {}, createdAt = {}",
                payment.getStatus(), payment.getAmount(), payment.getGatewayTransactionId(), payment.getCheckoutUrl(),
                payment.getIdempotencyKey().startsWith(BOOKING_ID + ":"), payment.getCreatedAt());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertEquals(TOTAL, payment.getAmount());
        assertEquals(TXN, payment.getGatewayTransactionId());
        assertEquals("https://pay/1", payment.getCheckoutUrl());
        assertTrue(payment.getIdempotencyKey().startsWith(BOOKING_ID + ":"));
        assertEquals(NOW, payment.getCreatedAt());
        verify(gateway).createCheckout(anyString(), eq(Money.vnd(TOTAL)), eq("Thanh toan don BKTEST0001"), eq(NOW.plusMinutes(10)));
    }

    // Viewpoint: VP4
    @Test
    public void test_initiate_ReusesOpenPayment_002() {
        // Có giao dịch đang mở đúng số tiền (1 record): trả lại giao dịch đó, không gọi cổng.
        booking.setStatus(BookingStatus.HELD);
        Payment open = payment(1L, TOTAL, PaymentStatus.PENDING);
        when(payments.findByBookingId(BOOKING_ID)).thenReturn(List.of(open));
        log.input("bookingId = {}, method = {}", BOOKING_ID, PaymentMethod.VIETQR);

        Payment payment = service.initiate(BOOKING_ID, PaymentMethod.VIETQR);

        log.output("paymentId = {}", payment.getId());
        assertSame(open, payment);
        verify(gateway, never()).createCheckout(anyString(), any(), anyString(), any());
    }

    // Viewpoint: VP4, VP3
    @Test
    public void test_initiate_SkipsClosedPayments_003() {
        // Nhiều giao dịch nhưng đều đã đóng (FAILED, UNCONFIRMED): tạo giao dịch mới.
        booking.setStatus(BookingStatus.HELD);
        when(payments.findByBookingId(BOOKING_ID)).thenReturn(List.of(payment(1L, TOTAL, PaymentStatus.FAILED),
                payment(2L, TOTAL, PaymentStatus.UNCONFIRMED)));
        log.input("bookingId = {}, method = {}", BOOKING_ID, PaymentMethod.CARD);

        Payment payment = service.initiate(BOOKING_ID, PaymentMethod.CARD);

        log.output("status = {}, method = {}", payment.getStatus(), payment.getMethod());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertEquals(PaymentMethod.CARD, payment.getMethod());
    }

    // Viewpoint: VP3
    @Test
    public void test_initiate_ConfirmedWithPendingChange_004() {
        // Đơn đã xác nhận có yêu cầu đổi chỗ: thu đúng phần chênh, hạn theo yêu cầu đổi.
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPaidAmount(TOTAL);
        booking.setPendingAmount(50_000L);
        booking.setPendingExpiresAt(NOW.plusMinutes(7));
        log.input("bookingId = {}, method = {}", BOOKING_ID, PaymentMethod.E_WALLET);

        Payment payment = service.initiate(BOOKING_ID, PaymentMethod.E_WALLET);

        log.output("amount = {}", payment.getAmount());
        assertEquals(50_000L, payment.getAmount());
        verify(gateway).createCheckout(anyString(), eq(Money.vnd(50_000)), anyString(), eq(NOW.plusMinutes(7)));
    }

    // Viewpoint: VP3
    @Test
    public void test_initiate_OpenPaymentOfOldAmount_005() {
        // Giao dịch đang mở khác số tiền (của yêu cầu đổi chỗ trước): chuyển UNCONFIRMED, ghi ngay, tạo giao dịch mới.
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPendingAmount(50_000L);
        booking.setPendingExpiresAt(NOW.plusMinutes(7));
        Payment stale = payment(1L, 30_000, PaymentStatus.PENDING);
        when(payments.findByBookingId(BOOKING_ID)).thenReturn(List.of(stale));
        log.input("bookingId = {}, method = {}", BOOKING_ID, PaymentMethod.VIETQR);

        Payment payment = service.initiate(BOOKING_ID, PaymentMethod.VIETQR);

        log.output("staleStatus = {}, newAmount = {}", stale.getStatus(), payment.getAmount());
        assertEquals(PaymentStatus.UNCONFIRMED, stale.getStatus());
        assertEquals(50_000L, payment.getAmount());
        assertNotSame(stale, payment);
        verify(payments).saveAndFlush(stale);
    }

    // Viewpoint: VP3
    @Test
    public void test_initiate_ConfirmedWithoutPendingChange_006() {
        // Đơn đã xác nhận, không còn gì phải trả.
        booking.setStatus(BookingStatus.CONFIRMED);
        log.input("bookingId = {}, method = {}", BOOKING_ID, PaymentMethod.VIETQR);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.initiate(BOOKING_ID, PaymentMethod.VIETQR));

        log.output("code = {}", thrown.code());
        assertEquals("BOOKING_NOT_PAYABLE", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_initiate_ExpiredBooking_007() {
        // Đơn đã hết hạn giữ chỗ.
        booking.setStatus(BookingStatus.EXPIRED);
        log.input("bookingId = {}, method = {}", BOOKING_ID, PaymentMethod.VIETQR);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.initiate(BOOKING_ID, PaymentMethod.VIETQR));

        log.output("code = {}", thrown.code());
        assertEquals("BOOKING_NOT_PAYABLE", thrown.code());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_initiate_NothingToPay_008() {
        // Cận dưới: đơn giữ chỗ có tổng 0.
        booking.setStatus(BookingStatus.HELD);
        booking.getCharges().clear();
        log.input("bookingId = {}, method = {}", BOOKING_ID, PaymentMethod.VIETQR);

        DomainException thrown = assertThrows(DomainException.class, () -> service.initiate(BOOKING_ID, PaymentMethod.VIETQR));

        log.output("code = {}", thrown.code());
        assertEquals("NOTHING_TO_PAY", thrown.code());
        verify(payments, never()).save(any());
    }

    // ---------- handleGatewayResult ----------

    // Viewpoint: VP3
    @Test
    public void test_handleGatewayResult_SuccessPending_009() {
        // Thành công, đúng số tiền: SUCCEEDED, báo BookingService.
        Payment payment = stored(PaymentStatus.PENDING);
        Money reported = Money.vnd(TOTAL);
        log.input("gatewayTransactionId = {}, success = {}, reportedAmount = {}", TXN, true, reported);

        Payment result = service.handleGatewayResult(TXN, true, reported);

        log.output("status = {}, completedAt = {}", result.getStatus(), result.getCompletedAt());
        assertEquals(PaymentStatus.SUCCEEDED, result.getStatus());
        assertEquals(NOW, result.getCompletedAt());
        verify(bookingService).onPaymentSucceeded(BOOKING_ID, Money.vnd(TOTAL));
        verify(payments).save(payment);
    }

    // Viewpoint: VP3
    @Test
    public void test_handleGatewayResult_SuccessUnconfirmed_010() {
        // Giao dịch UNCONFIRMED vẫn nhận kết quả thành công.
        stored(PaymentStatus.UNCONFIRMED);
        Money reported = Money.vnd(TOTAL);
        log.input("gatewayTransactionId = {}, success = {}, reportedAmount = {}", TXN, true, reported);

        Payment result = service.handleGatewayResult(TXN, true, reported);

        log.output("status = {}", result.getStatus());
        assertEquals(PaymentStatus.SUCCEEDED, result.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_handleGatewayResult_DuplicateSuccess_011() {
        // Webhook thành công lặp lại: bỏ qua.
        Payment payment = stored(PaymentStatus.SUCCEEDED);
        log.input("gatewayTransactionId = {}, success = {}, reportedAmount = {}", TXN, true, Money.vnd(TOTAL));

        Payment result = service.handleGatewayResult(TXN, true, Money.vnd(TOTAL));

        log.output("status = {}", result.getStatus());
        assertSame(payment, result);
        verify(bookingService, never()).onPaymentSucceeded(anyLong(), any());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_handleGatewayResult_AmountOneLess_012() {
        // Cổng báo thiếu 1 đồng.
        stored(PaymentStatus.PENDING);
        Money reported = Money.vnd(TOTAL - 1);
        log.input("gatewayTransactionId = {}, success = {}, reportedAmount = {}", TXN, true, reported);

        DomainException thrown = assertThrows(DomainException.class, () -> service.handleGatewayResult(TXN, true, reported));

        log.output("code = {}", thrown.code());
        assertEquals("AMOUNT_MISMATCH", thrown.code());
        verify(bookingService, never()).onPaymentSucceeded(anyLong(), any());
    }

    // Viewpoint: VP3
    @Test
    public void test_handleGatewayResult_SuccessFromInitiated_013() {
        // Giao dịch chưa gửi sang cổng (INITIATED) không nhận kết quả.
        stored(PaymentStatus.INITIATED);
        log.input("gatewayTransactionId = {}, success = {}, reportedAmount = {}", TXN, true, Money.vnd(TOTAL));

        DomainException thrown = assertThrows(DomainException.class, () -> service.handleGatewayResult(TXN, true, Money.vnd(TOTAL)));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_PAYMENT_STATE", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_handleGatewayResult_Failure_014() {
        // Thất bại: FAILED, báo BookingService.
        stored(PaymentStatus.PENDING);
        log.input("gatewayTransactionId = {}, success = {}, reportedAmount = {}", TXN, false, Money.ZERO);

        Payment result = service.handleGatewayResult(TXN, false, Money.ZERO);

        log.output("status = {}, completedAt = {}", result.getStatus(), result.getCompletedAt());
        assertEquals(PaymentStatus.FAILED, result.getStatus());
        assertEquals(NOW, result.getCompletedAt());
        verify(bookingService).onPaymentFailed(BOOKING_ID);
    }

    // Viewpoint: VP3
    @Test
    public void test_handleGatewayResult_DuplicateFailure_015() {
        // Webhook thất bại lặp lại: bỏ qua.
        stored(PaymentStatus.FAILED);
        log.input("gatewayTransactionId = {}, success = {}, reportedAmount = {}", TXN, false, Money.ZERO);

        Payment result = service.handleGatewayResult(TXN, false, Money.ZERO);

        log.output("status = {}", result.getStatus());
        assertEquals(PaymentStatus.FAILED, result.getStatus());
        verify(bookingService, never()).onPaymentFailed(anyLong());
    }

    // Viewpoint: VP3
    @Test
    public void test_handleGatewayResult_FailureAfterSuccess_016() {
        // Đã thành công rồi lại báo thất bại.
        stored(PaymentStatus.SUCCEEDED);
        log.input("gatewayTransactionId = {}, success = {}, reportedAmount = {}", TXN, false, Money.ZERO);

        DomainException thrown = assertThrows(DomainException.class, () -> service.handleGatewayResult(TXN, false, Money.ZERO));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_PAYMENT_STATE", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_handleGatewayResult_UnknownTransaction_017() {
        // Mã giao dịch không có.
        log.input("gatewayTransactionId = {}, success = {}, reportedAmount = {}", "khong-co", true, Money.vnd(TOTAL));

        NotFoundException thrown = assertThrows(NotFoundException.class,
                () -> service.handleGatewayResult("khong-co", true, Money.vnd(TOTAL)));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    private Payment payment(Long id, long amount, PaymentStatus status) {
        Payment p = new Payment();
        p.setId(id);
        p.setBooking(booking);
        p.setAmount(amount);
        p.setStatus(status);
        return p;
    }

    private Payment stored(PaymentStatus status) {
        Payment p = payment(1L, TOTAL, status);
        p.setGatewayTransactionId(TXN);
        when(payments.findByGatewayTransactionId(TXN)).thenReturn(Optional.of(p));
        return p;
    }
}
