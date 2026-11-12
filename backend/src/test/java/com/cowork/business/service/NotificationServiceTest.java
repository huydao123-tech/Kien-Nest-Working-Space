package com.cowork.business.service;

import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.Branch;
import com.cowork.dataaccess.entity.Customer;
import com.cowork.dataaccess.entity.NotificationJob;
import com.cowork.dataaccess.entity.ResourceType;
import com.cowork.dataaccess.entity.SpaceResource;
import com.cowork.dataaccess.entity.Staff;
import com.cowork.dataaccess.entity.Zone;
import com.cowork.dataaccess.enums.NotificationChannel;
import com.cowork.dataaccess.enums.NotificationJobStatus;
import com.cowork.dataaccess.enums.NotificationType;
import com.cowork.dataaccess.enums.ResourceTypeCode;
import com.cowork.dataaccess.enums.StaffRole;
import com.cowork.dataaccess.enums.StaffStatus;
import com.cowork.dataaccess.integration.NotificationSender;
import com.cowork.dataaccess.integration.NotificationSender.Recipient;
import com.cowork.dataaccess.repository.NotificationJobRepository;
import com.cowork.dataaccess.repository.StaffRepository;
import com.cowork.dataaccess.valueobject.ContactInfo;
import com.cowork.dataaccess.valueobject.Money;
import com.cowork.dataaccess.valueobject.TimeRange;
import com.cowork.support.MutableClock;
import com.cowork.support.TestCaseLogRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * UC26: nội dung tin nhắn gửi cho khách khi đơn đổi trạng thái, mã OTP, cảnh báo sắp hết giờ (FR-NOTI-03),
 * cảnh báo quá giờ cho quầy (FR-SYS-02). Viết theo {@code .claude/rules/unit-test.md}.
 */
public class NotificationServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 3, 9, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 3, 11, 0);
    private static final ContactInfo CONTACT = new ContactInfo("Lê C", "0911222333", "c@example.com");
    private static final Long BOOKING_ID = 100L;
    private static final Long BRANCH_ID = 1L;

    @Rule
    public final TestCaseLogRule log = new TestCaseLogRule();

    private NotificationSender sender;
    private NotificationJobRepository jobs;
    private StaffRepository staff;
    private MutableClock clock;
    private NotificationService service;
    private Booking booking;
    private Branch branch;

    @Before
    public void setUp() {
        sender = mock(NotificationSender.class);
        jobs = mock(NotificationJobRepository.class);
        staff = mock(StaffRepository.class);
        clock = new MutableClock(ZoneId.of("Asia/Ho_Chi_Minh"));
        clock.set(START);
        service = new NotificationService(sender, jobs, staff, clock);
        Customer customer = new Customer();
        customer.setContact(CONTACT);
        branch = new Branch();
        branch.setId(BRANCH_ID);
        SpaceResource resource = new SpaceResource("HD-01", "Hot desk 1", new ResourceType(ResourceTypeCode.HOT_DESK, "Hot desk", "Bàn"), 4);
        resource.setZone(new Zone(branch, "Tầng 1", 1, null));
        booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setCode("BKTEST0001");
        booking.setCustomer(customer);
        booking.setResource(resource);
        booking.setPeriod(new TimeRange(START, END));
    }

    // Viewpoint: VP7
    @Test
    public void test_bookingConfirmed_SendsCodeAndPeriod_001() {
        // Tin xác nhận gửi tới đúng khách, có mã đơn và khung giờ.
        log.input("booking = {}", booking.getCode());

        service.bookingConfirmed(booking);

        Recipient recipient = recipient();
        String message = message();
        log.output("recipient = {}, message = {}", recipient, message);
        assertEquals(new Recipient("Lê C", "0911222333", "c@example.com"), recipient);
        assertTrue(message.contains("BKTEST0001") && message.contains("09:00 03/11/2026") && message.contains("11:00 03/11/2026"));
    }

    // Viewpoint: VP7
    @Test
    public void test_bookingConfirmed_CustomerWithoutEmail_002() {
        // Khách không có email: người nhận có email rỗng.
        booking.getCustomer().setContact(new ContactInfo("Lê C", "0911222333", null));
        log.input("booking = {}", booking.getCode());

        service.bookingConfirmed(booking);

        Recipient recipient = recipient();
        log.output("email = {}", recipient.email());
        assertNull(recipient.email());
    }

    // Viewpoint: VP7
    @Test
    public void test_bookingConfirmed_NullCustomer_003() {
        // Đơn không có khách.
        booking.setCustomer(null);
        log.input("booking = {}", booking.getCode());

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.bookingConfirmed(booking));

        log.output("exception = {}", thrown.getClass().getSimpleName());
        assertEquals(NullPointerException.class, thrown.getClass());
        verify(sender, never()).send(any(), anyString());
    }

    // Viewpoint: VP7
    @Test
    public void test_holdExpired_Message_004() {
        // Hết thời gian giữ chỗ.
        log.input("booking = {}", booking.getCode());

        service.holdExpired(booking);

        String message = message();
        log.output("message = {}", message);
        assertTrue(message.contains("hết thời gian giữ chỗ"));
    }

    // Viewpoint: VP7
    @Test
    public void test_paymentFailed_Message_005() {
        // Thanh toán thất bại.
        log.input("booking = {}", booking.getCode());

        service.paymentFailed(booking);

        String message = message();
        log.output("message = {}", message);
        assertTrue(message.contains("thanh toán không thành công"));
    }

    // Viewpoint: VP1
    @Test
    public void test_bookingCancelled_RefundAmount_006() {
        // Hủy đơn: tin có số tiền hoàn.
        Money refund = Money.vnd(140_000);
        log.input("booking = {}, refund = {}", booking.getCode(), refund);

        service.bookingCancelled(booking, refund);

        String message = message();
        log.output("message = {}", message);
        assertTrue(message.contains("140000 VND"));
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_bookingRescheduled_WithRefund_007() {
        // Đổi chỗ, hoàn 1 đồng (cận dưới của nhánh có hoàn tiền): tin có dòng hoàn tiền.
        Money refund = Money.vnd(1);
        log.input("booking = {}, refund = {}", booking.getCode(), refund);

        service.bookingRescheduled(booking, refund);

        String message = message();
        log.output("message = {}", message);
        assertTrue(message.contains("được hoàn: 1 VND"));
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_bookingRescheduled_WithoutRefund_008() {
        // Đổi chỗ, không hoàn tiền: không có dòng hoàn tiền.
        Money refund = Money.ZERO;
        log.input("booking = {}, refund = {}", booking.getCode(), refund);

        service.bookingRescheduled(booking, refund);

        String message = message();
        log.output("message = {}", message);
        assertFalse(message.contains("hoàn"));
    }

    // Viewpoint: VP7
    @Test
    public void test_rescheduleCancelled_Message_009() {
        // Yêu cầu đổi chỗ bị hủy do không thanh toán: nhắc khung giờ cũ được giữ nguyên.
        log.input("booking = {}", booking.getCode());

        service.rescheduleCancelled(booking);

        String message = message();
        log.output("message = {}", message);
        assertTrue(message.contains("chưa được thanh toán") && message.contains("09:00 03/11/2026"));
    }

    // Viewpoint: VP2
    @Test
    public void test_otp_SendsToPhoneOnly_010() {
        // OTP gửi theo SĐT, không tên, không email; nội dung có mã.
        String phone = "0911222333";
        String code = "012345";
        log.input("phone = {}, code = {}", phone, code);

        service.otp(phone, code);

        Recipient recipient = recipient();
        String message = message();
        log.output("recipient = {}, message = {}", recipient, message);
        assertEquals(new Recipient(null, phone, null), recipient);
        assertTrue(message.contains(code));
    }

    // Viewpoint: VP1
    @Test
    public void test_refundAfterLatePayment_Message_011() {
        // Tiền về nhưng không dùng được: báo số tiền sẽ hoàn.
        Money amount = Money.vnd(110_000);
        log.input("booking = {}, amount = {}", booking.getCode(), amount);

        service.refundAfterLatePayment(booking, amount);

        String message = message();
        log.output("message = {}", message);
        assertTrue(message.contains("110000 VND"));
    }

    // ---------- notification.md: hoàn tiền xong, no-show, bảo trì ----------

    // Viewpoint: VP1
    @Test
    public void test_refundCompleted_Message_012() {
        // Nhân viên xác nhận đã hoàn: tin có mã đơn và số tiền.
        Money amount = Money.vnd(140_000);
        log.input("booking = {}, amount = {}", booking.getCode(), amount);

        service.refundCompleted(booking, amount);

        String message = message();
        log.output("message = {}", message);
        assertTrue(message.contains("Đã hoàn 140000 VND") && message.contains("BKTEST0001"));
    }

    // Viewpoint: VP7
    @Test
    public void test_noShow_Message_013() {
        // Không đến: tin có mã đơn, khung giờ, báo không hoàn tiền.
        log.input("booking = {}", booking.getCode());

        service.noShow(booking);

        String message = message();
        log.output("message = {}", message);
        assertTrue(message.contains("BKTEST0001") && message.contains("09:00 03/11/2026") && message.contains("không được hoàn tiền"));
    }

    // Viewpoint: VP7
    @Test
    public void test_resourceMaintenance_Message_014() {
        // Vị trí bảo trì: tin có mã vị trí, mã đơn, khung giờ.
        log.input("booking = {}", booking.getCode());

        service.resourceMaintenance(booking);

        String message = message();
        log.output("message = {}", message);
        assertTrue(message.contains("HD-01") && message.contains("BKTEST0001") && message.contains("bảo trì"));
    }

    // ---------- endWarning ----------

    // Viewpoint: VP3
    @Test
    public void test_endWarning_ExactSendTime_015() {
        // Cận dưới: đúng 15 phút trước giờ kết thúc (mặc định) → gửi, ghi notification_job SENT đúng mốc, kênh của khách.
        clock.set(END.minusMinutes(15));
        log.input("booking = {}, now = {}", booking.getCode(), END.minusMinutes(15));

        boolean sent = service.endWarning(booking);

        NotificationJob job = savedJob();
        log.output("sent = {}, type = {}, sendAt = {}, status = {}, channel = {}, sentAt = {}",
                sent, job.getType(), job.getSendAt(), job.getStatus(), job.getChannel(), job.getSentAt());
        assertTrue(sent);
        assertEquals(NotificationType.END_WARNING, job.getType());
        assertEquals(END.minusMinutes(15), job.getSendAt());
        assertEquals(NotificationJobStatus.SENT, job.getStatus());
        assertEquals(NotificationChannel.SMS, job.getChannel());
        assertEquals(END.minusMinutes(15), job.getSentAt());
        assertTrue(message().contains("Gia hạn"));
    }

    // Viewpoint: VP3
    @Test
    public void test_endWarning_OneSecondBeforeSendTime_016() {
        // Chưa tới mốc gửi 1 giây → không gửi, không ghi.
        clock.set(END.minusMinutes(15).minusSeconds(1));
        log.input("booking = {}, now = {}", booking.getCode(), END.minusMinutes(15).minusSeconds(1));

        boolean sent = service.endWarning(booking);

        log.output("sent = {}", sent);
        assertFalse(sent);
        verifyNoInteractions(sender, jobs);
    }

    // Viewpoint: VP3
    @Test
    public void test_endWarning_InsideWindow_017() {
        // Khoảng giữa: còn 5 phút → vẫn gửi (job trước đó lỡ mốc), mốc ghi vẫn là giờ kết thúc − 15 phút.
        clock.set(END.minusMinutes(5));
        log.input("booking = {}, now = {}", booking.getCode(), END.minusMinutes(5));

        boolean sent = service.endWarning(booking);

        NotificationJob job = savedJob();
        log.output("sent = {}, sendAt = {}", sent, job.getSendAt());
        assertTrue(sent);
        assertEquals(END.minusMinutes(15), job.getSendAt());
    }

    // Viewpoint: VP3
    @Test
    public void test_endWarning_AtEndTime_018() {
        // Cận trên: đúng giờ kết thúc → không gửi nữa (đã là chuyện quá giờ).
        clock.set(END);
        log.input("booking = {}, now = {}", booking.getCode(), END);

        boolean sent = service.endWarning(booking);

        log.output("sent = {}", sent);
        assertFalse(sent);
        verify(sender, never()).send(any(), anyString());
    }

    // Viewpoint: VP3
    @Test
    public void test_endWarning_AlreadySent_019() {
        // Đã gửi cho mốc này (job quét lần hai) → không gửi lại, không ghi thêm.
        clock.set(END.minusMinutes(10));
        when(jobs.existsByBookingIdAndTypeAndSendAt(BOOKING_ID, NotificationType.END_WARNING, END.minusMinutes(15))).thenReturn(true);
        log.input("booking = {}, now = {}", booking.getCode(), END.minusMinutes(10));

        boolean sent = service.endWarning(booking);

        log.output("sent = {}", sent);
        assertFalse(sent);
        verify(jobs, never()).save(any());
        verify(sender, never()).send(any(), anyString());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_endWarning_CustomerChoseThirtyMinutes_020() {
        // Khách chọn 30 phút (giá trị lớn nhất) và kênh Zalo: gửi từ 30 phút trước, ghi đúng mốc và kênh.
        booking.getCustomer().setNotifyBeforeMinutes(30);
        booking.getCustomer().setPreferredChannel(NotificationChannel.ZALO);
        clock.set(END.minusMinutes(30));
        log.input("booking = {}, now = {}, notifyBeforeMinutes = {}", booking.getCode(), END.minusMinutes(30), 30);

        boolean sent = service.endWarning(booking);

        NotificationJob job = savedJob();
        log.output("sent = {}, sendAt = {}, channel = {}", sent, job.getSendAt(), job.getChannel());
        assertTrue(sent);
        assertEquals(END.minusMinutes(30), job.getSendAt());
        assertEquals(NotificationChannel.ZALO, job.getChannel());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_endWarning_CustomerChoseTenMinutes_021() {
        // Khách chọn 10 phút (giá trị nhỏ nhất): còn 11 phút thì chưa gửi.
        booking.getCustomer().setNotifyBeforeMinutes(10);
        clock.set(END.minusMinutes(11));
        log.input("booking = {}, now = {}, notifyBeforeMinutes = {}", booking.getCode(), END.minusMinutes(11), 10);

        boolean sent = service.endWarning(booking);

        log.output("sent = {}", sent);
        assertFalse(sent);
    }

    // ---------- overstay ----------

    // Viewpoint: VP4, VP7
    @Test
    public void test_overstay_ManyReceptionists_022() {
        // Vòng lặp > 1 record: mỗi lễ tân của chi nhánh nhận một tin theo email (nhân viên không có SĐT).
        when(staff.findByBranchIdAndRoleAndStatusOrderByIdAsc(BRANCH_ID, StaffRole.RECEPTIONIST, StaffStatus.ACTIVE))
                .thenReturn(List.of(receptionist("Ngô Huy", "huy@cowork.example"), receptionist("Lý An", "an@cowork.example")));
        log.input("booking = {}", booking.getCode());

        int count = service.overstay(booking);

        ArgumentCaptor<Recipient> recipients = ArgumentCaptor.forClass(Recipient.class);
        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        verify(sender, times(2)).send(recipients.capture(), messages.capture());
        log.output("count = {}, recipients = {}, message = {}", count, recipients.getAllValues(), messages.getValue());
        assertEquals(2, count);
        assertEquals(List.of(new Recipient("Ngô Huy", null, "huy@cowork.example"), new Recipient("Lý An", null, "an@cowork.example")),
                recipients.getAllValues());
        assertTrue(messages.getValue().contains("HD-01") && messages.getValue().contains("BKTEST0001")
                && messages.getValue().contains("Lê C") && messages.getValue().contains("11:00 03/11/2026"));
    }

    // Viewpoint: VP4
    @Test
    public void test_overstay_OneReceptionist_023() {
        // Vòng lặp 1 record.
        when(staff.findByBranchIdAndRoleAndStatusOrderByIdAsc(BRANCH_ID, StaffRole.RECEPTIONIST, StaffStatus.ACTIVE))
                .thenReturn(List.of(receptionist("Ngô Huy", "huy@cowork.example")));
        log.input("booking = {}", booking.getCode());

        int count = service.overstay(booking);

        log.output("count = {}", count);
        assertEquals(1, count);
        verify(sender).send(any(), anyString());
    }

    // Viewpoint: VP4
    @Test
    public void test_overstay_NoReceptionist_024() {
        // Vòng lặp 0 record: chi nhánh không có lễ tân → không gửi, chỉ ghi log.
        log.input("booking = {}", booking.getCode());

        int count = service.overstay(booking);

        log.output("count = {}", count);
        assertEquals(0, count);
        verify(sender, never()).send(any(), anyString());
    }

    // Viewpoint: VP7
    @Test
    public void test_overstay_ResourceWithoutZone_025() {
        // Vị trí chưa gán khu vực (không biết chi nhánh) → không tìm nhân viên, không gửi.
        booking.getResource().setZone(null);
        log.input("booking = {}", booking.getCode());

        int count = service.overstay(booking);

        log.output("count = {}", count);
        assertEquals(0, count);
        verifyNoInteractions(staff, sender);
    }

    private NotificationJob savedJob() {
        ArgumentCaptor<NotificationJob> captor = ArgumentCaptor.forClass(NotificationJob.class);
        verify(jobs).save(captor.capture());
        return captor.getValue();
    }

    private Staff receptionist(String fullName, String email) {
        return new Staff(branch, fullName, email, "hash", StaffRole.RECEPTIONIST, 0L);
    }

    private Recipient recipient() {
        ArgumentCaptor<Recipient> captor = ArgumentCaptor.forClass(Recipient.class);
        verify(sender).send(captor.capture(), anyString());
        return captor.getValue();
    }

    private String message() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sender).send(any(), captor.capture());
        return captor.getValue();
    }
}
