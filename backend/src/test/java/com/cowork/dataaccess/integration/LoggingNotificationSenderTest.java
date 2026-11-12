package com.cowork.dataaccess.integration;

import com.cowork.dataaccess.integration.NotificationSender.Recipient;
import com.cowork.support.TestCaseLogRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * NFR-09: che số điện thoại khi ghi log tin nhắn; người nhận không có SĐT (lễ tân nhận cảnh báo quá giờ — notification.md)
 * không làm lỗi. Viết theo {@code .claude/rules/unit-test.md}.
 */
public class LoggingNotificationSenderTest {

    @Rule
    public final TestCaseLogRule log = new TestCaseLogRule();

    private LoggingNotificationSender sender;

    @Before
    public void setUp() {
        sender = new LoggingNotificationSender();
    }

    // Viewpoint: VP7
    @Test
    public void test_maskPhone_NullPhone_001() {
        // Nhân viên chỉ có email: ghi "-" thay cho SĐT.
        String phone = null;
        log.input("phone = {}", phone);

        String masked = LoggingNotificationSender.maskPhone(phone);

        log.output("masked = {}", masked);
        assertEquals("-", masked);
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_maskPhone_EmptyPhone_002() {
        // Chuỗi rỗng (0 ký tự, nhánh <= 4): che toàn bộ.
        String phone = "";
        log.input("phone = {}", phone);

        String masked = LoggingNotificationSender.maskPhone(phone);

        log.output("masked = {}", masked);
        assertEquals("****", masked);
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_maskPhone_FourDigits_003() {
        // Cận trên của nhánh che toàn bộ: 4 ký tự.
        String phone = "1234";
        log.input("phone = {}", phone);

        String masked = LoggingNotificationSender.maskPhone(phone);

        log.output("masked = {}", masked);
        assertEquals("****", masked);
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_maskPhone_FiveDigits_004() {
        // Cận dưới của nhánh giữ 3 số cuối: 5 ký tự.
        String phone = "12345";
        log.input("phone = {}", phone);

        String masked = LoggingNotificationSender.maskPhone(phone);

        log.output("masked = {}", masked);
        assertEquals("**345", masked);
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_maskPhone_TenDigits_005() {
        // Số di động 10 chữ số: chỉ giữ 3 số cuối.
        String phone = "0911222333";
        log.input("phone = {}", phone);

        String masked = LoggingNotificationSender.maskPhone(phone);

        log.output("masked = {}", masked);
        assertEquals("*******333", masked);
    }

    // Viewpoint: VP2, VP7
    @Test
    public void test_send_StaffWithoutPhone_006() {
        // Gửi cho nhân viên (không SĐT, tên có dấu 2–3 byte) không ném lỗi.
        Recipient recipient = new Recipient("Ngô Huy ệ", null, "huy@cowork.example");
        String message = "Quá giờ: vị trí HD-01 !@#";
        log.input("recipient = {}, message = {}", recipient, message);

        Throwable error = null;
        try {
            sender.send(recipient, message);
        } catch (RuntimeException e) {
            error = e;
        }

        log.output("error = {}", error);
        assertNull(error);
    }
}
