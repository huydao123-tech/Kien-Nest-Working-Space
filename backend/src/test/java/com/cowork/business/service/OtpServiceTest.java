package com.cowork.business.service;

import com.cowork.business.exception.DomainException;
import com.cowork.config.AuthProperties;
import com.cowork.dataaccess.entity.OtpCode;
import com.cowork.dataaccess.repository.OtpCodeRepository;
import com.cowork.support.MutableClock;
import com.cowork.support.TestCaseLogRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC01, FR-AUTH-02: gửi và kiểm tra mã OTP — hạn 5 phút, gửi lại sau 60 giây, sai tối đa 5 lần.
 * Viết theo {@code .claude/rules/unit-test.md}. Mã băm bằng BCrypt thật (cost 4).
 */
public class OtpServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 8, 0);
    private static final String PHONE = "0911222333";
    private static final String CODE = "123456";

    @Rule
    public final TestCaseLogRule log = new TestCaseLogRule();

    private MutableClock clock;
    private OtpCodeRepository otpCodes;
    private NotificationService notifications;
    private PasswordEncoder encoder;
    private OtpService service;

    @Before
    public void setUp() {
        clock = new MutableClock(ZoneId.of("Asia/Ho_Chi_Minh"));
        clock.set(NOW);
        otpCodes = mock(OtpCodeRepository.class);
        notifications = mock(NotificationService.class);
        OtpGenerator generator = mock(OtpGenerator.class);
        when(generator.next()).thenReturn(CODE);
        encoder = new BCryptPasswordEncoder(4);
        AuthProperties properties = new AuthProperties("secret", "cowork", Duration.ofHours(8), Duration.ofMinutes(5), 5,
                Duration.ofSeconds(60));
        service = new OtpService(otpCodes, generator, encoder, notifications, properties, clock);
        when(otpCodes.save(any(OtpCode.class))).thenAnswer(call -> call.getArgument(0));
    }

    // ---------- send ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_send_FirstCode_001() {
        // Chưa có mã nào: tạo mã, chỉ lưu bản băm, gửi SMS, trả hạn 5 phút.
        log.input("phone = {}", PHONE);

        LocalDateTime expiresAt = service.send(PHONE);

        OtpCode saved = saved();
        boolean hashMatches = encoder.matches(CODE, saved.getCodeHash());
        log.output("expiresAt = {}, hashMatches = {}, createdAt = {}", expiresAt, hashMatches, saved.getCreatedAt());
        assertEquals(NOW.plusMinutes(5), expiresAt);
        assertTrue(hashMatches);
        assertEquals(NOW, saved.getCreatedAt());
        verify(notifications).otp(PHONE, CODE);
    }

    // Viewpoint: VP3
    @Test
    public void test_send_OneSecondBeforeCooldown_002() {
        // Gửi lại sau 59 giây: quá sớm.
        when(otpCodes.findFirstByPhoneOrderByCreatedAtDescIdDesc(PHONE))
                .thenReturn(Optional.of(otp(NOW.minusSeconds(59), NOW.plusMinutes(4))));
        log.input("phone = {}", PHONE);

        DomainException thrown = assertThrows(DomainException.class, () -> service.send(PHONE));

        log.output("code = {}", thrown.code());
        assertEquals("OTP_RESEND_TOO_SOON", thrown.code());
        verify(notifications, never()).otp(anyString(), anyString());
    }

    // Viewpoint: VP3
    @Test
    public void test_send_ExactCooldown_003() {
        // Cận: đúng 60 giây sau lần trước thì được gửi lại.
        when(otpCodes.findFirstByPhoneOrderByCreatedAtDescIdDesc(PHONE))
                .thenReturn(Optional.of(otp(NOW.minusSeconds(60), NOW.plusMinutes(4))));
        log.input("phone = {}", PHONE);

        LocalDateTime expiresAt = service.send(PHONE);

        log.output("expiresAt = {}", expiresAt);
        assertEquals(NOW.plusMinutes(5), expiresAt);
        verify(notifications).otp(PHONE, CODE);
    }

    // Viewpoint: VP2
    @Test
    public void test_send_InvalidPhone_004() {
        // SĐT sai định dạng (có chữ).
        String phone = "09112223a3";
        log.input("phone = {}", phone);

        DomainException thrown = assertThrows(DomainException.class, () -> service.send(phone));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CONTACT", thrown.code());
    }

    // Viewpoint: VP2
    @Test
    public void test_send_EmptyPhone_005() {
        // SĐT rỗng.
        String phone = "";
        log.input("phone = {}", phone);

        DomainException thrown = assertThrows(DomainException.class, () -> service.send(phone));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CONTACT", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_send_NullPhone_006() {
        // SĐT null.
        String phone = null;
        log.input("phone = {}", phone);

        DomainException thrown = assertThrows(DomainException.class, () -> service.send(phone));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CONTACT", thrown.code());
    }

    // ---------- verify ----------

    // Viewpoint: VP3
    @Test
    public void test_verify_CorrectCode_007() {
        // Mã đúng, còn hạn: đánh dấu đã dùng.
        OtpCode otp = stored(NOW.plusMinutes(5), 0);
        log.input("phone = {}, code = {}", PHONE, CODE);

        service.verify(PHONE, CODE);

        log.output("consumedAt = {}", otp.getConsumedAt());
        assertEquals(NOW, otp.getConsumedAt());
    }

    // Viewpoint: VP7
    @Test
    public void test_verify_NoCodeSent_008() {
        // Số này chưa xin mã nào.
        log.input("phone = {}, code = {}", PHONE, CODE);

        DomainException thrown = assertThrows(DomainException.class, () -> service.verify(PHONE, CODE));

        log.output("code = {}", thrown.code());
        assertEquals("OTP_INVALID", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_verify_AlreadyConsumed_009() {
        // Mã đã dùng.
        OtpCode otp = stored(NOW.plusMinutes(5), 0);
        otp.setConsumedAt(NOW.minusMinutes(1));
        log.input("phone = {}, code = {}", PHONE, CODE);

        DomainException thrown = assertThrows(DomainException.class, () -> service.verify(PHONE, CODE));

        log.output("code = {}", thrown.code());
        assertEquals("OTP_INVALID", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_verify_ExpiresNow_010() {
        // Cận: hết hạn đúng lúc này.
        stored(NOW, 0);
        log.input("phone = {}, code = {}", PHONE, CODE);

        DomainException thrown = assertThrows(DomainException.class, () -> service.verify(PHONE, CODE));

        log.output("code = {}", thrown.code());
        assertEquals("OTP_EXPIRED", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_verify_OneSecondBeforeExpiry_011() {
        // Còn 1 giây: vẫn dùng được.
        OtpCode otp = stored(NOW.plusSeconds(1), 0);
        log.input("phone = {}, code = {}", PHONE, CODE);

        service.verify(PHONE, CODE);

        log.output("consumedAt = {}", otp.getConsumedAt());
        assertEquals(NOW, otp.getConsumedAt());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_verify_FourAttemptsStillAllowed_012() {
        // Đã sai 4 lần (dưới giới hạn 5): lần đúng vẫn qua.
        OtpCode otp = stored(NOW.plusMinutes(5), 4);
        log.input("phone = {}, code = {}", PHONE, CODE);

        service.verify(PHONE, CODE);

        log.output("consumedAt = {}", otp.getConsumedAt());
        assertEquals(NOW, otp.getConsumedAt());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_verify_FiveAttemptsBlocked_013() {
        // Cận trên: đã sai đủ 5 lần, mã đúng cũng bị chặn.
        OtpCode otp = stored(NOW.plusMinutes(5), 5);
        log.input("phone = {}, code = {}", PHONE, CODE);

        DomainException thrown = assertThrows(DomainException.class, () -> service.verify(PHONE, CODE));

        log.output("code = {}, consumedAt = {}", thrown.code(), otp.getConsumedAt());
        assertEquals("OTP_TOO_MANY_ATTEMPTS", thrown.code());
        assertNull(otp.getConsumedAt());
    }

    // Viewpoint: VP3
    @Test
    public void test_verify_WrongCodeCountsAttempt_014() {
        // Mã sai: tăng số lần sai và lưu lại.
        OtpCode otp = stored(NOW.plusMinutes(5), 1);
        log.input("phone = {}, code = {}", PHONE, "000000");

        DomainException thrown = assertThrows(DomainException.class, () -> service.verify(PHONE, "000000"));

        log.output("code = {}, attempts = {}", thrown.code(), otp.getAttempts());
        assertEquals("OTP_INVALID", thrown.code());
        assertEquals(2, otp.getAttempts());
        verify(otpCodes).save(otp);
    }

    // Viewpoint: VP7
    @Test
    public void test_verify_NullCode_015() {
        // Mã null: tính là sai.
        OtpCode otp = stored(NOW.plusMinutes(5), 0);
        log.input("phone = {}, code = {}", PHONE, null);

        DomainException thrown = assertThrows(DomainException.class, () -> service.verify(PHONE, null));

        log.output("code = {}, attempts = {}", thrown.code(), otp.getAttempts());
        assertEquals("OTP_INVALID", thrown.code());
        assertEquals(1, otp.getAttempts());
    }

    private OtpCode otp(LocalDateTime createdAt, LocalDateTime expiresAt) {
        return new OtpCode(PHONE, encoder.encode(CODE), createdAt, expiresAt);
    }

    /** Mã gần nhất của {@link #PHONE}, hết hạn lúc {@code expiresAt}, đã sai {@code attempts} lần. */
    private OtpCode stored(LocalDateTime expiresAt, int attempts) {
        OtpCode otp = otp(NOW.minusMinutes(1), expiresAt);
        otp.setAttempts(attempts);
        when(otpCodes.findFirstByPhoneOrderByCreatedAtDescIdDesc(PHONE)).thenReturn(Optional.of(otp));
        return otp;
    }

    private OtpCode saved() {
        ArgumentCaptor<OtpCode> captor = ArgumentCaptor.forClass(OtpCode.class);
        verify(otpCodes).save(captor.capture());
        return captor.getValue();
    }
}
