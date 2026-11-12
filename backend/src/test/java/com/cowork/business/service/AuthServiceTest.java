package com.cowork.business.service;

import com.cowork.business.exception.AuthenticationFailedException;
import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.business.service.TokenService.IssuedToken;
import com.cowork.dataaccess.entity.Branch;
import com.cowork.dataaccess.entity.Customer;
import com.cowork.dataaccess.entity.Staff;
import com.cowork.dataaccess.enums.MembershipPassStatus;
import com.cowork.dataaccess.enums.NotificationChannel;
import com.cowork.dataaccess.enums.StaffRole;
import com.cowork.dataaccess.enums.StaffStatus;
import com.cowork.dataaccess.repository.CustomerRepository;
import com.cowork.dataaccess.repository.StaffRepository;
import com.cowork.dataaccess.valueobject.ContactInfo;
import com.cowork.dto.Actor;
import com.cowork.dto.AuthDtos.AuthResponse;
import com.cowork.dto.AuthDtos.ChangePasswordRequest;
import com.cowork.dto.AuthDtos.LoginRequest;
import com.cowork.dto.AuthDtos.OtpLoginRequest;
import com.cowork.dto.AuthDtos.ProfileResponse;
import com.cowork.dto.AuthDtos.RegisterEmailRequest;
import com.cowork.dto.AuthDtos.RegisterPhoneRequest;
import com.cowork.dto.AuthDtos.ResetPasswordRequest;
import com.cowork.dto.AuthDtos.StaffLoginRequest;
import com.cowork.dto.AuthDtos.UpdateProfileRequest;
import com.cowork.dto.CommerceDtos.MembershipPassResponse;
import com.cowork.dto.CommerceDtos.MembershipSummary;
import com.cowork.support.MutableClock;
import com.cowork.support.TestCaseLogRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC01, UC23, FR-AUTH-02, FR-AUTH-03, FR-AUTH-05: đăng ký, đăng nhập, hồ sơ, sửa hồ sơ, đổi / quên mật khẩu. Viết theo {@code .claude/rules/unit-test.md}.
 * OTP và JWT được giả lập; mật khẩu băm bằng BCrypt thật (cost 4 cho nhanh).
 */
public class AuthServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 8, 0);
    private static final String PHONE = "0911222333";
    private static final String OTP = "123456";
    private static final String PASSWORD = "matkhau123";
    private static final String EMAIL = "c@example.com";
    private static final String OLD_EMAIL = "old@example.com";
    private static final String STAFF_EMAIL = "lan@cowork.example";
    private static final String STAFF_PASSWORD = "Cowork@123";
    /** Mỗi ký tự chiếm 1 byte UTF-8: chữ cái, số, ký hiệu. */
    private static final String TEXT_1_BYTE = "Nguyen Van A1 !#$";
    /** Mỗi ký tự chiếm 2 byte UTF-8. */
    private static final String TEXT_2_BYTE = "àêô";
    /** Mỗi ký tự chiếm 3 byte UTF-8. */
    private static final String TEXT_3_BYTE = "ệặữ";
    private static final String NEW_PASSWORD = "matkhaumoi456";
    private static final MembershipSummary SUMMARY = new MembershipSummary(600, List.of(new MembershipPassResponse(
            3L, "Gói 10 giờ", 600, NOW.minusDays(1), NOW.plusDays(29), MembershipPassStatus.ACTIVE, true)));

    @Rule
    public final TestCaseLogRule log = new TestCaseLogRule();

    private CustomerRepository customers;
    private StaffRepository staff;
    private OtpService otpService;
    private TokenService tokens;
    private PasswordEncoder encoder;
    private MembershipService memberships;
    private AuthService service;

    @Before
    public void setUp() {
        MutableClock clock = new MutableClock(ZoneId.of("Asia/Ho_Chi_Minh"));
        clock.set(NOW);
        customers = mock(CustomerRepository.class);
        staff = mock(StaffRepository.class);
        otpService = mock(OtpService.class);
        tokens = mock(TokenService.class);
        encoder = new BCryptPasswordEncoder(4);
        memberships = mock(MembershipService.class);
        service = new AuthService(customers, staff, otpService, tokens, encoder, memberships, clock);
        when(memberships.summaryOf(any())).thenReturn(SUMMARY);
        when(tokens.issue(any(), anyString(), any())).thenReturn(new IssuedToken("jwt", Instant.EPOCH));
        when(customers.save(any(Customer.class))).thenAnswer(call -> {
            Customer c = call.getArgument(0);
            if (c.getId() == null) {
                c.setId(42L);
            }
            return c;
        });
    }

    // ---------- registerByPhone ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_registerByPhone_NewPhoneWithoutEmail_001() {
        // SĐT mới, không email: tạo khách mới, SĐT đã xác thực.
        RegisterPhoneRequest request = phoneRequest(PHONE, "Lê C", PASSWORD, null);
        log.input("request = {}", request);

        AuthResponse response = service.registerByPhone(request);

        Customer saved = savedCustomer();
        boolean passwordMatches = encoder.matches(PASSWORD, saved.getPasswordHash());
        log.output("createdAt = {}, phoneVerifiedAt = {}, contact = {}, passwordMatches = {}, accessToken = {}, tokenType = {}, userId = {}, userPhoneVerified = {}",
                saved.getCreatedAt(), saved.getPhoneVerifiedAt(), saved.getContact(), passwordMatches,
                response.accessToken(), response.tokenType(), response.user().id(), response.user().phoneVerified());
        assertEquals(NOW, saved.getCreatedAt());
        assertEquals(NOW, saved.getPhoneVerifiedAt());
        assertEquals(new ContactInfo("Lê C", PHONE, null), saved.getContact());
        assertTrue(passwordMatches);
        assertEquals("jwt", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(Long.valueOf(42L), response.user().id());
        assertTrue(response.user().phoneVerified());
        verify(otpService).verify(PHONE, OTP);
        verify(tokens).issue(Actor.customer(42L), "Lê C", null);
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_registerByPhone_GuestKeepsOldEmail_002() {
        // FR-AUTH-03: SĐT có đơn vãng lai, không nhập email: dùng lại dòng cũ, giữ email và ngày tạo cũ.
        Customer guest = guest(7L, OLD_EMAIL);
        LocalDateTime createdAt = guest.getCreatedAt();
        when(customers.findByPhone(PHONE)).thenReturn(Optional.of(guest));
        RegisterPhoneRequest request = phoneRequest(PHONE, "Lê C", PASSWORD, null);
        log.input("request = {}", request);

        AuthResponse response = service.registerByPhone(request);

        log.output("userId = {}, contact = {}, createdAt = {}, phoneVerifiedAt = {}",
                response.user().id(), guest.getContact(), guest.getCreatedAt(), guest.getPhoneVerifiedAt());
        assertEquals(Long.valueOf(7L), response.user().id());
        assertEquals(new ContactInfo("Lê C", PHONE, OLD_EMAIL), guest.getContact());
        assertEquals(createdAt, guest.getCreatedAt());
        assertEquals(NOW, guest.getPhoneVerifiedAt());
        verify(customers).save(guest);
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_registerByPhone_GuestWithoutEmail_003() {
        // Khách vãng lai không có email, cũng không nhập email: email vẫn rỗng.
        Customer guest = guest(7L, null);
        when(customers.findByPhone(PHONE)).thenReturn(Optional.of(guest));
        RegisterPhoneRequest request = phoneRequest(PHONE, "Lê C", PASSWORD, null);
        log.input("request = {}", request);

        service.registerByPhone(request);

        log.output("email = {}", guest.getContact().email());
        assertNull(guest.getContact().email());
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_registerByPhone_GuestWithoutContact_004() {
        // Dòng khách cũ chưa có contact: không lỗi, email rỗng.
        Customer guest = guest(7L, null);
        guest.setContact(null);
        when(customers.findByPhone(PHONE)).thenReturn(Optional.of(guest));
        RegisterPhoneRequest request = phoneRequest(PHONE, "Lê C", PASSWORD, null);
        log.input("request = {}", request);

        service.registerByPhone(request);

        log.output("contact = {}", guest.getContact());
        assertEquals(new ContactInfo("Lê C", PHONE, null), guest.getContact());
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_registerByPhone_NewEmailReplacesOldEmail_005() {
        // Nhập email mới: thay email cũ, bỏ khoảng trắng hai đầu.
        Customer guest = guest(7L, OLD_EMAIL);
        when(customers.findByPhone(PHONE)).thenReturn(Optional.of(guest));
        RegisterPhoneRequest request = phoneRequest(PHONE, "Lê C", PASSWORD, "  " + EMAIL + "  ");
        log.input("request = {}", request);

        service.registerByPhone(request);

        log.output("email = {}", guest.getContact().email());
        assertEquals(EMAIL, guest.getContact().email());
        verify(customers).findAccountByEmail(EMAIL);
    }

    // Viewpoint: VP3
    @Test
    public void test_registerByPhone_EmptyEmail_006() {
        // Email rỗng coi như không nhập: không kiểm tra trùng email.
        RegisterPhoneRequest request = phoneRequest(PHONE, "Lê C", PASSWORD, "");
        log.input("request = {}", request);

        service.registerByPhone(request);

        String email = savedCustomer().getContact().email();
        log.output("email = {}", email);
        assertNull(email);
        verify(customers, never()).findAccountByEmail(any());
    }

    // Viewpoint: VP3
    @Test
    public void test_registerByPhone_BlankEmail_007() {
        // Email chỉ có khoảng trắng coi như không nhập.
        RegisterPhoneRequest request = phoneRequest(PHONE, "Lê C", PASSWORD, "   ");
        log.input("request = {}", request);

        service.registerByPhone(request);

        String email = savedCustomer().getContact().email();
        log.output("email = {}", email);
        assertNull(email);
        verify(customers, never()).findAccountByEmail(any());
    }

    // Viewpoint: VP3
    @Test
    public void test_registerByPhone_PhoneAlreadyRegistered_008() {
        // SĐT đã có tài khoản: từ chối trước khi dùng OTP.
        Customer account = guest(7L, OLD_EMAIL);
        account.setPasswordHash("x");
        when(customers.findByPhone(PHONE)).thenReturn(Optional.of(account));
        RegisterPhoneRequest request = phoneRequest(PHONE, "Lê C", PASSWORD, null);
        log.input("request = {}", request);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.registerByPhone(request));

        log.output("code = {}", thrown.code());
        assertEquals("ACCOUNT_ALREADY_EXISTS", thrown.code());
        verify(otpService, never()).verify(any(), any());
        verify(customers, never()).save(any());
    }

    // Viewpoint: VP3
    @Test
    public void test_registerByPhone_EmailAlreadyUsed_009() {
        // Email đã thuộc tài khoản khác: từ chối trước khi dùng OTP.
        when(customers.findAccountByEmail(EMAIL)).thenReturn(Optional.of(guest(8L, EMAIL)));
        RegisterPhoneRequest request = phoneRequest(PHONE, "Lê C", PASSWORD, EMAIL);
        log.input("request = {}", request);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.registerByPhone(request));

        log.output("code = {}", thrown.code());
        assertEquals("EMAIL_ALREADY_USED", thrown.code());
        verify(otpService, never()).verify(any(), any());
        verify(customers, never()).save(any());
    }

    // Viewpoint: VP3
    @Test
    public void test_registerByPhone_InvalidOtp_010() {
        // OTP sai: không lưu, không cấp token.
        doThrow(new DomainException("OTP_INVALID", "sai")).when(otpService).verify(PHONE, OTP);
        RegisterPhoneRequest request = phoneRequest(PHONE, "Lê C", PASSWORD, null);
        log.input("request = {}", request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.registerByPhone(request));

        log.output("code = {}", thrown.code());
        assertEquals("OTP_INVALID", thrown.code());
        verify(customers, never()).save(any());
        verify(tokens, never()).issue(any(), any(), any());
    }

    // Viewpoint: VP3
    @Test
    public void test_registerByPhone_InvalidEmailFormat_011() {
        // Email sai định dạng: INVALID_CONTACT, không dùng OTP.
        RegisterPhoneRequest request = phoneRequest(PHONE, "Lê C", PASSWORD, "sai-email");
        log.input("request = {}", request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.registerByPhone(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CONTACT", thrown.code());
        verify(otpService, never()).verify(any(), any());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_registerByPhone_PhoneTooShort_012() {
        // Cận dưới sai: 9 chữ số.
        RegisterPhoneRequest request = phoneRequest("091122233", "Lê C", PASSWORD, null);
        log.input("request = {}", request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.registerByPhone(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CONTACT", thrown.code());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_registerByPhone_PhoneMaxLength_013() {
        // Cận trên đúng dạng 0xxx: đúng 10 chữ số.
        RegisterPhoneRequest request = phoneRequest("0999999999", "Lê C", PASSWORD, null);
        log.input("request = {}", request);

        service.registerByPhone(request);

        String phone = savedCustomer().getContact().phone();
        log.output("phone = {}", phone);
        assertEquals("0999999999", phone);
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_registerByPhone_PhoneTooLong_014() {
        // Cận trên sai: 11 chữ số.
        RegisterPhoneRequest request = phoneRequest("09112223334", "Lê C", PASSWORD, null);
        log.input("request = {}", request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.registerByPhone(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CONTACT", thrown.code());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_registerByPhone_PhoneWithCountryCode_015() {
        // Nhánh đầu số +84 + 9 chữ số.
        RegisterPhoneRequest request = phoneRequest("+84911222333", "Lê C", PASSWORD, null);
        log.input("request = {}", request);

        service.registerByPhone(request);

        String phone = savedCustomer().getContact().phone();
        log.output("phone = {}", phone);
        assertEquals("+84911222333", phone);
    }

    // Viewpoint: VP1
    @Test
    public void test_registerByPhone_EmptyPhone_016() {
        // SĐT rỗng.
        RegisterPhoneRequest request = phoneRequest("", "Lê C", PASSWORD, null);
        log.input("request = {}", request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.registerByPhone(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CONTACT", thrown.code());
    }

    // Viewpoint: VP1, VP7
    @Test
    public void test_registerByPhone_NullPhone_017() {
        // SĐT null.
        RegisterPhoneRequest request = phoneRequest(null, "Lê C", PASSWORD, null);
        log.input("request = {}", request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.registerByPhone(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CONTACT", thrown.code());
    }

    // Viewpoint: VP2
    @Test
    public void test_registerByPhone_Text1Byte_018() {
        // Họ tên, mật khẩu ký tự 1 byte.
        RegisterPhoneRequest request = phoneRequest(PHONE, TEXT_1_BYTE, TEXT_1_BYTE, null);
        log.input("request = {}", request);

        service.registerByPhone(request);

        Customer saved = savedCustomer();
        boolean passwordMatches = encoder.matches(TEXT_1_BYTE, saved.getPasswordHash());
        log.output("fullName = {}, passwordMatches = {}", saved.getContact().fullName(), passwordMatches);
        assertEquals(TEXT_1_BYTE, saved.getContact().fullName());
        assertTrue(passwordMatches);
    }

    // Viewpoint: VP2
    @Test
    public void test_registerByPhone_Text2Byte_019() {
        // Họ tên, mật khẩu ký tự 2 byte.
        RegisterPhoneRequest request = phoneRequest(PHONE, TEXT_2_BYTE, TEXT_2_BYTE, null);
        log.input("request = {}", request);

        service.registerByPhone(request);

        Customer saved = savedCustomer();
        boolean passwordMatches = encoder.matches(TEXT_2_BYTE, saved.getPasswordHash());
        log.output("fullName = {}, passwordMatches = {}", saved.getContact().fullName(), passwordMatches);
        assertEquals(TEXT_2_BYTE, saved.getContact().fullName());
        assertTrue(passwordMatches);
    }

    // Viewpoint: VP2
    @Test
    public void test_registerByPhone_Text3Byte_020() {
        // Họ tên, mật khẩu ký tự 3 byte.
        RegisterPhoneRequest request = phoneRequest(PHONE, TEXT_3_BYTE, TEXT_3_BYTE, null);
        log.input("request = {}", request);

        service.registerByPhone(request);

        Customer saved = savedCustomer();
        boolean passwordMatches = encoder.matches(TEXT_3_BYTE, saved.getPasswordHash());
        log.output("fullName = {}, passwordMatches = {}", saved.getContact().fullName(), passwordMatches);
        assertEquals(TEXT_3_BYTE, saved.getContact().fullName());
        assertTrue(passwordMatches);
    }

    // Viewpoint: VP3
    @Test
    public void test_registerByPhone_BlankFullName_021() {
        // Họ tên chỉ có khoảng trắng: bỏ khoảng trắng còn rỗng → INVALID_CONTACT.
        RegisterPhoneRequest request = phoneRequest(PHONE, "   ", PASSWORD, null);
        log.input("request = {}", request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.registerByPhone(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CONTACT", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_registerByPhone_NullFullName_022() {
        // Họ tên null: @NotBlank ở controller chặn trước, gọi thẳng service thì NullPointerException.
        RegisterPhoneRequest request = phoneRequest(PHONE, null, PASSWORD, null);
        log.input("request = {}", request);

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.registerByPhone(request));

        log.output("exception = {}", thrown.getClass().getSimpleName());
    }

    // Viewpoint: VP7
    @Test
    public void test_registerByPhone_NullRequest_023() {
        // Request null.
        RegisterPhoneRequest request = null;
        log.input("request = {}", request);

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.registerByPhone(request));

        log.output("exception = {}", thrown.getClass().getSimpleName());
    }

    // ---------- registerByEmail ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_registerByEmail_NewEmailAndPhone_024() {
        // Email, SĐT đều mới: tạo tài khoản, SĐT chưa xác thực.
        RegisterEmailRequest request = emailRequest("  " + EMAIL + " ", PHONE, " Lê C ", PASSWORD);
        log.input("request = {}", request);

        AuthResponse response = service.registerByEmail(request);

        Customer saved = savedCustomer();
        boolean passwordMatches = encoder.matches(PASSWORD, saved.getPasswordHash());
        log.output("contact = {}, createdAt = {}, phoneVerifiedAt = {}, passwordMatches = {}, userId = {}, userPhoneVerified = {}, accessToken = {}",
                saved.getContact(), saved.getCreatedAt(), saved.getPhoneVerifiedAt(), passwordMatches,
                response.user().id(), response.user().phoneVerified(), response.accessToken());
        assertEquals(new ContactInfo("Lê C", PHONE, EMAIL), saved.getContact());
        assertEquals(NOW, saved.getCreatedAt());
        assertNull(saved.getPhoneVerifiedAt());
        assertTrue(passwordMatches);
        assertEquals(Long.valueOf(42L), response.user().id());
        assertFalse(response.user().phoneVerified());
        assertEquals("jwt", response.accessToken());
        verify(otpService, never()).verify(any(), any());
    }

    // Viewpoint: VP3
    @Test
    public void test_registerByEmail_EmailAlreadyUsed_025() {
        // Email đã thuộc tài khoản khác.
        when(customers.findAccountByEmail(EMAIL)).thenReturn(Optional.of(guest(8L, EMAIL)));
        RegisterEmailRequest request = emailRequest(EMAIL, PHONE, "Lê C", PASSWORD);
        log.input("request = {}", request);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.registerByEmail(request));

        log.output("code = {}", thrown.code());
        assertEquals("EMAIL_ALREADY_USED", thrown.code());
        verify(customers, never()).save(any());
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_registerByEmail_PhoneAlreadyRegistered_026() {
        // SĐT đã có tài khoản.
        Customer account = guest(7L, OLD_EMAIL);
        account.setPasswordHash("x");
        when(customers.findByPhone(PHONE)).thenReturn(Optional.of(account));
        RegisterEmailRequest request = emailRequest(EMAIL, PHONE, "Lê C", PASSWORD);
        log.input("request = {}", request);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.registerByEmail(request));

        log.output("code = {}", thrown.code());
        assertEquals("ACCOUNT_ALREADY_EXISTS", thrown.code());
        verify(customers, never()).save(any());
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_registerByEmail_GuestPhoneNotVerified_027() {
        // FR-AUTH-03: SĐT của khách vãng lai chưa xác thực thì không được nhận đơn cũ.
        when(customers.findByPhone(PHONE)).thenReturn(Optional.of(guest(7L, OLD_EMAIL)));
        RegisterEmailRequest request = emailRequest(EMAIL, PHONE, "Lê C", PASSWORD);
        log.input("request = {}", request);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.registerByEmail(request));

        log.output("code = {}", thrown.code());
        assertEquals("PHONE_VERIFICATION_REQUIRED", thrown.code());
        verify(customers, never()).save(any());
    }

    // Viewpoint: VP3
    @Test
    public void test_registerByEmail_InvalidEmailFormat_028() {
        // Email sai định dạng.
        RegisterEmailRequest request = emailRequest("sai-email", PHONE, "Lê C", PASSWORD);
        log.input("request = {}", request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.registerByEmail(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CONTACT", thrown.code());
        verify(customers, never()).save(any());
    }

    // Viewpoint: VP3
    @Test
    public void test_registerByEmail_EmptyEmail_029() {
        // Email rỗng: service không chặn (ContactInfo.hasEmail() = false), việc chặn nằm ở @NotBlank của controller.
        RegisterEmailRequest request = emailRequest("", PHONE, "Lê C", PASSWORD);
        log.input("request = {}", request);

        service.registerByEmail(request);

        String email = savedCustomer().getContact().email();
        log.output("email = {}", email);
        assertEquals("", email);
    }

    // Viewpoint: VP7
    @Test
    public void test_registerByEmail_NullEmail_030() {
        // Email null.
        RegisterEmailRequest request = emailRequest(null, PHONE, "Lê C", PASSWORD);
        log.input("request = {}", request);

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.registerByEmail(request));

        log.output("exception = {}", thrown.getClass().getSimpleName());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_registerByEmail_PhoneTooShort_031() {
        // Cận dưới sai: 9 chữ số.
        RegisterEmailRequest request = emailRequest(EMAIL, "091122233", "Lê C", PASSWORD);
        log.input("request = {}", request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.registerByEmail(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CONTACT", thrown.code());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_registerByEmail_PhoneMaxLength_032() {
        // Cận trên đúng: 10 chữ số.
        RegisterEmailRequest request = emailRequest(EMAIL, "0999999999", "Lê C", PASSWORD);
        log.input("request = {}", request);

        service.registerByEmail(request);

        String phone = savedCustomer().getContact().phone();
        log.output("phone = {}", phone);
        assertEquals("0999999999", phone);
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_registerByEmail_PhoneTooLong_033() {
        // Cận trên sai: 11 chữ số.
        RegisterEmailRequest request = emailRequest(EMAIL, "09112223334", "Lê C", PASSWORD);
        log.input("request = {}", request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.registerByEmail(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CONTACT", thrown.code());
    }

    // Viewpoint: VP1
    @Test
    public void test_registerByEmail_EmptyPhone_034() {
        // SĐT rỗng.
        RegisterEmailRequest request = emailRequest(EMAIL, "", "Lê C", PASSWORD);
        log.input("request = {}", request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.registerByEmail(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CONTACT", thrown.code());
    }

    // Viewpoint: VP1, VP7
    @Test
    public void test_registerByEmail_NullPhone_035() {
        // SĐT null.
        RegisterEmailRequest request = emailRequest(EMAIL, null, "Lê C", PASSWORD);
        log.input("request = {}", request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.registerByEmail(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CONTACT", thrown.code());
    }

    // Viewpoint: VP2
    @Test
    public void test_registerByEmail_Text1Byte_036() {
        // Họ tên, mật khẩu ký tự 1 byte.
        RegisterEmailRequest request = emailRequest(EMAIL, PHONE, TEXT_1_BYTE, TEXT_1_BYTE);
        log.input("request = {}", request);

        service.registerByEmail(request);

        Customer saved = savedCustomer();
        boolean passwordMatches = encoder.matches(TEXT_1_BYTE, saved.getPasswordHash());
        log.output("fullName = {}, passwordMatches = {}", saved.getContact().fullName(), passwordMatches);
        assertEquals(TEXT_1_BYTE, saved.getContact().fullName());
        assertTrue(passwordMatches);
    }

    // Viewpoint: VP2
    @Test
    public void test_registerByEmail_Text2Byte_037() {
        // Họ tên, mật khẩu ký tự 2 byte.
        RegisterEmailRequest request = emailRequest(EMAIL, PHONE, TEXT_2_BYTE, TEXT_2_BYTE);
        log.input("request = {}", request);

        service.registerByEmail(request);

        Customer saved = savedCustomer();
        boolean passwordMatches = encoder.matches(TEXT_2_BYTE, saved.getPasswordHash());
        log.output("fullName = {}, passwordMatches = {}", saved.getContact().fullName(), passwordMatches);
        assertEquals(TEXT_2_BYTE, saved.getContact().fullName());
        assertTrue(passwordMatches);
    }

    // Viewpoint: VP2
    @Test
    public void test_registerByEmail_Text3Byte_038() {
        // Họ tên, mật khẩu ký tự 3 byte.
        RegisterEmailRequest request = emailRequest(EMAIL, PHONE, TEXT_3_BYTE, TEXT_3_BYTE);
        log.input("request = {}", request);

        service.registerByEmail(request);

        Customer saved = savedCustomer();
        boolean passwordMatches = encoder.matches(TEXT_3_BYTE, saved.getPasswordHash());
        log.output("fullName = {}, passwordMatches = {}", saved.getContact().fullName(), passwordMatches);
        assertEquals(TEXT_3_BYTE, saved.getContact().fullName());
        assertTrue(passwordMatches);
    }

    // Viewpoint: VP7
    @Test
    public void test_registerByEmail_NullRequest_039() {
        // Request null.
        RegisterEmailRequest request = null;
        log.input("request = {}", request);

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.registerByEmail(request));

        log.output("exception = {}", thrown.getClass().getSimpleName());
    }

    // ---------- login ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_login_ByPhoneTrimsSpaces_040() {
        // Nhánh không có @: tìm theo SĐT, bỏ khoảng trắng hai đầu.
        Customer account = account(7L);
        when(customers.findByPhone(PHONE)).thenReturn(Optional.of(account));
        LoginRequest request = new LoginRequest(" " + PHONE + " ", PASSWORD);
        log.input("request = {}", request);

        AuthResponse response = service.login(request);

        log.output("userId = {}", response.user().id());
        assertEquals(Long.valueOf(7L), response.user().id());
        verify(customers, never()).findAccountByEmail(any());
        verify(tokens).issue(Actor.customer(7L), "Khách cũ", null);
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_login_ByEmail_041() {
        // Nhánh có @: tìm theo email.
        when(customers.findAccountByEmail(OLD_EMAIL)).thenReturn(Optional.of(account(7L)));
        LoginRequest request = new LoginRequest(OLD_EMAIL, PASSWORD);
        log.input("request = {}", request);

        AuthResponse response = service.login(request);

        log.output("userId = {}", response.user().id());
        assertEquals(Long.valueOf(7L), response.user().id());
        verify(customers, never()).findByPhone(any());
    }

    // Viewpoint: VP3
    @Test
    public void test_login_WrongPasswordByPhone_042() {
        // Sai mật khẩu khi đăng nhập bằng SĐT.
        when(customers.findByPhone(PHONE)).thenReturn(Optional.of(account(7L)));
        LoginRequest request = new LoginRequest(PHONE, "sai-mat-khau");
        log.input("request = {}", request);

        AuthenticationFailedException thrown = assertThrows(AuthenticationFailedException.class, () -> service.login(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CREDENTIALS", thrown.code());
        verify(tokens, never()).issue(any(), any(), any());
    }

    // Viewpoint: VP3
    @Test
    public void test_login_WrongPasswordByEmail_043() {
        // Sai mật khẩu khi đăng nhập bằng email.
        when(customers.findAccountByEmail(OLD_EMAIL)).thenReturn(Optional.of(account(7L)));
        LoginRequest request = new LoginRequest(OLD_EMAIL, "sai-mat-khau");
        log.input("request = {}", request);

        AuthenticationFailedException thrown = assertThrows(AuthenticationFailedException.class, () -> service.login(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CREDENTIALS", thrown.code());
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_login_PhoneNotExisted_044() {
        // SĐT không tồn tại.
        LoginRequest request = new LoginRequest("0999999999", PASSWORD);
        log.input("request = {}", request);

        AuthenticationFailedException thrown = assertThrows(AuthenticationFailedException.class, () -> service.login(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CREDENTIALS", thrown.code());
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_login_EmailNotExisted_045() {
        // Email không tồn tại.
        LoginRequest request = new LoginRequest("khong@example.com", PASSWORD);
        log.input("request = {}", request);

        AuthenticationFailedException thrown = assertThrows(AuthenticationFailedException.class, () -> service.login(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CREDENTIALS", thrown.code());
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_login_GuestWithoutPassword_046() {
        // Khách vãng lai chưa có mật khẩu không đăng nhập được, dù đoán đúng SĐT.
        when(customers.findByPhone(PHONE)).thenReturn(Optional.of(guest(7L, OLD_EMAIL)));
        LoginRequest request = new LoginRequest(PHONE, PASSWORD);
        log.input("request = {}", request);

        AuthenticationFailedException thrown = assertThrows(AuthenticationFailedException.class, () -> service.login(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CREDENTIALS", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_login_EmptyLogin_047() {
        // Chuỗi rỗng: đi nhánh SĐT, không tìm thấy.
        LoginRequest request = new LoginRequest("", PASSWORD);
        log.input("request = {}", request);

        AuthenticationFailedException thrown = assertThrows(AuthenticationFailedException.class, () -> service.login(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CREDENTIALS", thrown.code());
        verify(customers).findByPhone("");
    }

    // Viewpoint: VP3
    @Test
    public void test_login_LoginOnlyAtSign_048() {
        // Cận của điều kiện có @: chỉ một ký tự @ vẫn đi nhánh email.
        LoginRequest request = new LoginRequest("@", PASSWORD);
        log.input("request = {}", request);

        AuthenticationFailedException thrown = assertThrows(AuthenticationFailedException.class, () -> service.login(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CREDENTIALS", thrown.code());
        verify(customers).findAccountByEmail("@");
    }

    // Viewpoint: VP2
    @Test
    public void test_login_Password1Byte_049() {
        // Mật khẩu ký tự 1 byte.
        Customer account = account(7L);
        account.setPasswordHash(encoder.encode(TEXT_1_BYTE));
        when(customers.findByPhone(PHONE)).thenReturn(Optional.of(account));
        LoginRequest request = new LoginRequest(PHONE, TEXT_1_BYTE);
        log.input("request = {}", request);

        Long userId = service.login(request).user().id();

        log.output("userId = {}", userId);
        assertEquals(Long.valueOf(7L), userId);
    }

    // Viewpoint: VP2
    @Test
    public void test_login_AccountAndPassword2Byte_050() {
        // Tài khoản và mật khẩu ký tự 2 byte.
        String email = TEXT_2_BYTE + "@example.com";
        Customer account = account(7L);
        account.setPasswordHash(encoder.encode(TEXT_2_BYTE));
        when(customers.findAccountByEmail(email)).thenReturn(Optional.of(account));
        LoginRequest request = new LoginRequest(email, TEXT_2_BYTE);
        log.input("request = {}", request);

        Long userId = service.login(request).user().id();

        log.output("userId = {}", userId);
        assertEquals(Long.valueOf(7L), userId);
    }

    // Viewpoint: VP2
    @Test
    public void test_login_AccountAndPassword3Byte_051() {
        // Tài khoản và mật khẩu ký tự 3 byte.
        String email = TEXT_3_BYTE + "@example.com";
        Customer account = account(7L);
        account.setPasswordHash(encoder.encode(TEXT_3_BYTE));
        when(customers.findAccountByEmail(email)).thenReturn(Optional.of(account));
        LoginRequest request = new LoginRequest(email, TEXT_3_BYTE);
        log.input("request = {}", request);

        Long userId = service.login(request).user().id();

        log.output("userId = {}", userId);
        assertEquals(Long.valueOf(7L), userId);
    }

    // Viewpoint: VP7
    @Test
    public void test_login_NullLogin_052() {
        // Tên đăng nhập null.
        LoginRequest request = new LoginRequest(null, PASSWORD);
        log.input("request = {}", request);

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.login(request));

        log.output("exception = {}", thrown.getClass().getSimpleName());
    }

    // Viewpoint: VP7
    @Test
    public void test_login_NullRequest_053() {
        // Request null.
        LoginRequest request = null;
        log.input("request = {}", request);

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.login(request));

        log.output("exception = {}", thrown.getClass().getSimpleName());
    }

    // ---------- loginWithOtp ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_loginWithOtp_PhoneNotVerified_054() {
        // SĐT chưa xác thực: đăng nhập OTP thành công thì ghi nhận đã xác thực.
        Customer account = account(7L);
        when(customers.findByPhone(PHONE)).thenReturn(Optional.of(account));
        OtpLoginRequest request = new OtpLoginRequest(PHONE, OTP);
        log.input("request = {}", request);

        AuthResponse response = service.loginWithOtp(request);

        verify(otpService).verify(PHONE, OTP);
        log.output("phoneVerifiedAt = {}, userPhoneVerified = {}", account.getPhoneVerifiedAt(), response.user().phoneVerified());
        assertEquals(NOW, account.getPhoneVerifiedAt());
        assertTrue(response.user().phoneVerified());
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_loginWithOtp_PhoneAlreadyVerified_055() {
        // SĐT đã xác thực từ trước: giữ nguyên mốc cũ.
        LocalDateTime verifiedAt = NOW.minusDays(3);
        Customer account = account(7L);
        account.setPhoneVerifiedAt(verifiedAt);
        when(customers.findByPhone(PHONE)).thenReturn(Optional.of(account));
        OtpLoginRequest request = new OtpLoginRequest(PHONE, OTP);
        log.input("request = {}", request);

        service.loginWithOtp(request);

        log.output("phoneVerifiedAt = {}", account.getPhoneVerifiedAt());
        assertEquals(verifiedAt, account.getPhoneVerifiedAt());
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_loginWithOtp_PhoneNotExisted_056() {
        // SĐT chưa có trong hệ thống: không dùng OTP.
        OtpLoginRequest request = new OtpLoginRequest(PHONE, OTP);
        log.input("request = {}", request);

        AuthenticationFailedException thrown = assertThrows(AuthenticationFailedException.class, () -> service.loginWithOtp(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CREDENTIALS", thrown.code());
        verify(otpService, never()).verify(any(), any());
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_loginWithOtp_GuestWithoutPassword_057() {
        // Khách vãng lai chưa có mật khẩu: chưa phải tài khoản, không dùng OTP.
        when(customers.findByPhone(PHONE)).thenReturn(Optional.of(guest(7L, OLD_EMAIL)));
        OtpLoginRequest request = new OtpLoginRequest(PHONE, OTP);
        log.input("request = {}", request);

        AuthenticationFailedException thrown = assertThrows(AuthenticationFailedException.class, () -> service.loginWithOtp(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CREDENTIALS", thrown.code());
        verify(otpService, never()).verify(any(), any());
    }

    // Viewpoint: VP3
    @Test
    public void test_loginWithOtp_InvalidOtp_058() {
        // OTP sai: không ghi nhận xác thực, không cấp token.
        Customer account = account(7L);
        when(customers.findByPhone(PHONE)).thenReturn(Optional.of(account));
        doThrow(new DomainException("OTP_INVALID", "sai")).when(otpService).verify(PHONE, "000000");
        OtpLoginRequest request = new OtpLoginRequest(PHONE, "000000");
        log.input("request = {}", request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.loginWithOtp(request));

        log.output("code = {}, phoneVerifiedAt = {}", thrown.code(), account.getPhoneVerifiedAt());
        assertEquals("OTP_INVALID", thrown.code());
        assertNull(account.getPhoneVerifiedAt());
        verify(tokens, never()).issue(any(), any(), any());
    }

    // Viewpoint: VP1, VP7
    @Test
    public void test_loginWithOtp_NullPhone_059() {
        // SĐT null.
        OtpLoginRequest request = new OtpLoginRequest(null, OTP);
        log.input("request = {}", request);

        AuthenticationFailedException thrown = assertThrows(AuthenticationFailedException.class, () -> service.loginWithOtp(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CREDENTIALS", thrown.code());
    }

    // Viewpoint: VP1
    @Test
    public void test_loginWithOtp_EmptyPhone_060() {
        // SĐT rỗng.
        OtpLoginRequest request = new OtpLoginRequest("", OTP);
        log.input("request = {}", request);

        AuthenticationFailedException thrown = assertThrows(AuthenticationFailedException.class, () -> service.loginWithOtp(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CREDENTIALS", thrown.code());
    }

    // Viewpoint: VP2
    @Test
    public void test_loginWithOtp_Phone1Byte_061() {
        // SĐT ký tự 1 byte không phải số: không tìm thấy tài khoản.
        OtpLoginRequest request = new OtpLoginRequest(TEXT_1_BYTE, OTP);
        log.input("request = {}", request);

        AuthenticationFailedException thrown = assertThrows(AuthenticationFailedException.class, () -> service.loginWithOtp(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CREDENTIALS", thrown.code());
    }

    // Viewpoint: VP2
    @Test
    public void test_loginWithOtp_Phone2Byte_062() {
        // SĐT ký tự 2 byte: không tìm thấy tài khoản.
        OtpLoginRequest request = new OtpLoginRequest(TEXT_2_BYTE, OTP);
        log.input("request = {}", request);

        AuthenticationFailedException thrown = assertThrows(AuthenticationFailedException.class, () -> service.loginWithOtp(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CREDENTIALS", thrown.code());
    }

    // Viewpoint: VP2
    @Test
    public void test_loginWithOtp_Phone3Byte_063() {
        // SĐT ký tự 3 byte: không tìm thấy tài khoản.
        OtpLoginRequest request = new OtpLoginRequest(TEXT_3_BYTE, OTP);
        log.input("request = {}", request);

        AuthenticationFailedException thrown = assertThrows(AuthenticationFailedException.class, () -> service.loginWithOtp(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CREDENTIALS", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_loginWithOtp_NullRequest_064() {
        // Request null.
        OtpLoginRequest request = null;
        log.input("request = {}", request);

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.loginWithOtp(request));

        log.output("exception = {}", thrown.getClass().getSimpleName());
    }

    // ---------- staffLogin ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_staffLogin_StaffWithoutBranch_065() {
        // Nhân viên không thuộc chi nhánh nào: token không có chi nhánh. Email được bỏ khoảng trắng hai đầu.
        Staff member = staffMember(5L, null, StaffStatus.ACTIVE);
        when(staff.findByEmailIgnoreCase(STAFF_EMAIL)).thenReturn(Optional.of(member));
        StaffLoginRequest request = new StaffLoginRequest(" " + STAFF_EMAIL + " ", STAFF_PASSWORD);
        log.input("request = {}", request);

        AuthResponse response = service.staffLogin(request);

        log.output("accessToken = {}, tokenType = {}, role = {}, branchId = {}",
                response.accessToken(), response.tokenType(), response.user().role(), response.user().branchId());
        assertEquals("jwt", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(StaffRole.MANAGER, response.user().role());
        assertNull(response.user().branchId());
        verify(tokens).issue(Actor.staff(5L, StaffRole.MANAGER), "Lan", null);
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_staffLogin_StaffWithBranch_066() {
        // Nhân viên thuộc chi nhánh: token mang id chi nhánh.
        Branch branch = new Branch();
        branch.setId(3L);
        when(staff.findByEmailIgnoreCase(STAFF_EMAIL)).thenReturn(Optional.of(staffMember(5L, branch, StaffStatus.ACTIVE)));
        StaffLoginRequest request = new StaffLoginRequest(STAFF_EMAIL, STAFF_PASSWORD);
        log.input("request = {}", request);

        AuthResponse response = service.staffLogin(request);

        log.output("branchId = {}", response.user().branchId());
        assertEquals(Long.valueOf(3L), response.user().branchId());
        verify(tokens).issue(Actor.staff(5L, StaffRole.MANAGER), "Lan", 3L);
    }

    // Viewpoint: VP3
    @Test
    public void test_staffLogin_LockedCorrectPassword_067() {
        // Tài khoản bị khóa, đúng mật khẩu: ACCOUNT_LOCKED, không cấp token.
        when(staff.findByEmailIgnoreCase(STAFF_EMAIL)).thenReturn(Optional.of(staffMember(5L, null, StaffStatus.LOCKED)));
        StaffLoginRequest request = new StaffLoginRequest(STAFF_EMAIL, STAFF_PASSWORD);
        log.input("request = {}", request);

        AuthenticationFailedException thrown = assertThrows(AuthenticationFailedException.class, () -> service.staffLogin(request));

        log.output("code = {}", thrown.code());
        assertEquals("ACCOUNT_LOCKED", thrown.code());
        verify(tokens, never()).issue(any(), any(), any());
    }

    // Viewpoint: VP3
    @Test
    public void test_staffLogin_LockedWrongPassword_068() {
        // Tài khoản bị khóa, sai mật khẩu: không để lộ trạng thái khóa.
        when(staff.findByEmailIgnoreCase(STAFF_EMAIL)).thenReturn(Optional.of(staffMember(5L, null, StaffStatus.LOCKED)));
        StaffLoginRequest request = new StaffLoginRequest(STAFF_EMAIL, "sai-mat-khau");
        log.input("request = {}", request);

        AuthenticationFailedException thrown = assertThrows(AuthenticationFailedException.class, () -> service.staffLogin(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CREDENTIALS", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_staffLogin_ActiveWrongPassword_069() {
        // Tài khoản hoạt động, sai mật khẩu.
        when(staff.findByEmailIgnoreCase(STAFF_EMAIL)).thenReturn(Optional.of(staffMember(5L, null, StaffStatus.ACTIVE)));
        StaffLoginRequest request = new StaffLoginRequest(STAFF_EMAIL, "sai-mat-khau");
        log.input("request = {}", request);

        AuthenticationFailedException thrown = assertThrows(AuthenticationFailedException.class, () -> service.staffLogin(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CREDENTIALS", thrown.code());
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_staffLogin_EmailNotExisted_070() {
        // Email không tồn tại.
        StaffLoginRequest request = new StaffLoginRequest("khong@cowork.example", STAFF_PASSWORD);
        log.input("request = {}", request);

        AuthenticationFailedException thrown = assertThrows(AuthenticationFailedException.class, () -> service.staffLogin(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CREDENTIALS", thrown.code());
    }

    // Viewpoint: VP2
    @Test
    public void test_staffLogin_Password1Byte_071() {
        // Mật khẩu ký tự 1 byte.
        Staff member = staffMember(5L, null, StaffStatus.ACTIVE);
        member.setPasswordHash(encoder.encode(TEXT_1_BYTE));
        when(staff.findByEmailIgnoreCase(STAFF_EMAIL)).thenReturn(Optional.of(member));
        StaffLoginRequest request = new StaffLoginRequest(STAFF_EMAIL, TEXT_1_BYTE);
        log.input("request = {}", request);

        Long userId = service.staffLogin(request).user().id();

        log.output("userId = {}", userId);
        assertEquals(Long.valueOf(5L), userId);
    }

    // Viewpoint: VP2
    @Test
    public void test_staffLogin_EmailAndPassword2Byte_072() {
        // Email và mật khẩu ký tự 2 byte.
        String email = TEXT_2_BYTE + "@cowork.example";
        Staff member = staffMember(5L, null, StaffStatus.ACTIVE);
        member.setPasswordHash(encoder.encode(TEXT_2_BYTE));
        when(staff.findByEmailIgnoreCase(email)).thenReturn(Optional.of(member));
        StaffLoginRequest request = new StaffLoginRequest(email, TEXT_2_BYTE);
        log.input("request = {}", request);

        Long userId = service.staffLogin(request).user().id();

        log.output("userId = {}", userId);
        assertEquals(Long.valueOf(5L), userId);
    }

    // Viewpoint: VP2
    @Test
    public void test_staffLogin_EmailAndPassword3Byte_073() {
        // Email và mật khẩu ký tự 3 byte.
        String email = TEXT_3_BYTE + "@cowork.example";
        Staff member = staffMember(5L, null, StaffStatus.ACTIVE);
        member.setPasswordHash(encoder.encode(TEXT_3_BYTE));
        when(staff.findByEmailIgnoreCase(email)).thenReturn(Optional.of(member));
        StaffLoginRequest request = new StaffLoginRequest(email, TEXT_3_BYTE);
        log.input("request = {}", request);

        Long userId = service.staffLogin(request).user().id();

        log.output("userId = {}", userId);
        assertEquals(Long.valueOf(5L), userId);
    }

    // Viewpoint: VP7
    @Test
    public void test_staffLogin_NullEmail_074() {
        // Email null.
        StaffLoginRequest request = new StaffLoginRequest(null, STAFF_PASSWORD);
        log.input("request = {}", request);

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.staffLogin(request));

        log.output("exception = {}", thrown.getClass().getSimpleName());
    }

    // Viewpoint: VP7
    @Test
    public void test_staffLogin_NullRequest_075() {
        // Request null.
        StaffLoginRequest request = null;
        log.input("request = {}", request);

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.staffLogin(request));

        log.output("exception = {}", thrown.getClass().getSimpleName());
    }

    // ---------- profile ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_profile_CustomerFound_076() {
        // Nhánh khách, có dữ liệu: kèm số phút gói thành viên còn lại (FR-AUTH-05).
        when(customers.findById(7L)).thenReturn(Optional.of(account(7L)));
        Actor actor = Actor.customer(7L);
        log.input("actor = {}", actor);

        ProfileResponse profile = service.profile(actor);

        log.output("type = {}, id = {}, phone = {}, membership = {}", profile.type(), profile.id(), profile.phone(),
                profile.membership());
        assertEquals(Actor.Type.CUSTOMER, profile.type());
        assertEquals(Long.valueOf(7L), profile.id());
        assertEquals(PHONE, profile.phone());
        assertSame(SUMMARY, profile.membership());
        verify(staff, never()).findById(any());
        verify(memberships).summaryOf(7L);
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_profile_CustomerNotFound_077() {
        // Nhánh khách, không có dữ liệu.
        Actor actor = Actor.customer(7L);
        log.input("actor = {}", actor);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.profile(actor));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_profile_StaffFound_078() {
        // Nhánh nhân viên, có dữ liệu.
        when(staff.findById(5L)).thenReturn(Optional.of(staffMember(5L, null, StaffStatus.ACTIVE)));
        Actor actor = Actor.staff(5L, StaffRole.MANAGER);
        log.input("actor = {}", actor);

        ProfileResponse profile = service.profile(actor);

        log.output("type = {}, id = {}, role = {}, membership = {}", profile.type(), profile.id(), profile.role(),
                profile.membership());
        assertEquals(Actor.Type.STAFF, profile.type());
        assertEquals(Long.valueOf(5L), profile.id());
        assertEquals(StaffRole.MANAGER, profile.role());
        assertNull(profile.membership());
        verify(customers, never()).findById(any());
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_profile_StaffNotFound_079() {
        // Nhánh nhân viên, không có dữ liệu.
        Actor actor = Actor.staff(5L, StaffRole.MANAGER);
        log.input("actor = {}", actor);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.profile(actor));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_profile_GuestActor_080() {
        // Khách vãng lai không có id: đi nhánh khách, không tìm thấy.
        Actor actor = Actor.GUEST;
        log.input("actor = {}", actor);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.profile(actor));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_profile_NullActor_081() {
        // Actor null.
        Actor actor = null;
        log.input("actor = {}", actor);

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.profile(actor));

        log.output("exception = {}", thrown.getClass().getSimpleName());
    }

    // ---------- updateProfile ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_updateProfile_AllFields_082() {
        // Sửa họ tên, email, tùy chọn thông báo; SĐT giữ nguyên; trả kèm số phút gói.
        Customer customer = account(7L);
        when(customers.findById(7L)).thenReturn(Optional.of(customer));
        UpdateProfileRequest request = new UpdateProfileRequest("  Lê Văn C  ", EMAIL, 30, NotificationChannel.EMAIL);
        Actor actor = Actor.customer(7L);
        log.input("actor = {}, request = {}", actor, request);

        ProfileResponse profile = service.updateProfile(actor, request);

        log.output("contact = {}, notifyBeforeMinutes = {}, preferredChannel = {}, membership = {}", customer.getContact(),
                customer.getNotifyBeforeMinutes(), customer.getPreferredChannel(), profile.membership());
        assertEquals(new ContactInfo("Lê Văn C", PHONE, EMAIL), customer.getContact());
        assertEquals(30, customer.getNotifyBeforeMinutes());
        assertEquals(NotificationChannel.EMAIL, customer.getPreferredChannel());
        assertSame(SUMMARY, profile.membership());
        verify(customers).save(customer);
    }

    // Viewpoint: VP1, VP2
    @Test
    public void test_updateProfile_BlankEmailRemovesEmail_083() {
        // Email chỉ có khoảng trắng: xóa email; tùy chọn thông báo bỏ trống thì giữ nguyên.
        Customer customer = account(7L);
        when(customers.findById(7L)).thenReturn(Optional.of(customer));
        UpdateProfileRequest request = new UpdateProfileRequest("Lê C", "   ", null, null);
        Actor actor = Actor.customer(7L);
        log.input("actor = {}, request = {}", actor, request);

        service.updateProfile(actor, request);

        log.output("email = {}, notifyBeforeMinutes = {}, preferredChannel = {}", customer.getContact().email(),
                customer.getNotifyBeforeMinutes(), customer.getPreferredChannel());
        assertNull(customer.getContact().email());
        assertEquals(15, customer.getNotifyBeforeMinutes());
        assertEquals(NotificationChannel.SMS, customer.getPreferredChannel());
        verify(customers, never()).findAccountByEmail(anyString());
    }

    // Viewpoint: VP3
    @Test
    public void test_updateProfile_EmailUsedByOtherAccount_084() {
        // Email đã là email đăng nhập của tài khoản khác.
        Customer customer = account(7L);
        when(customers.findById(7L)).thenReturn(Optional.of(customer));
        when(customers.findAccountByEmail(EMAIL)).thenReturn(Optional.of(account(8L)));
        UpdateProfileRequest request = new UpdateProfileRequest("Lê C", EMAIL, null, null);
        Actor actor = Actor.customer(7L);
        log.input("actor = {}, request = {}", actor, request);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.updateProfile(actor, request));

        log.output("code = {}, email = {}", thrown.code(), customer.getContact().email());
        assertEquals("EMAIL_ALREADY_USED", thrown.code());
        assertEquals(OLD_EMAIL, customer.getContact().email());
        verify(customers, never()).save(any());
    }

    // Viewpoint: VP3
    @Test
    public void test_updateProfile_EmailOfOwnAccount_085() {
        // Email trùng chính tài khoản đang sửa: hợp lệ.
        Customer customer = account(7L);
        when(customers.findById(7L)).thenReturn(Optional.of(customer));
        when(customers.findAccountByEmail(OLD_EMAIL)).thenReturn(Optional.of(customer));
        UpdateProfileRequest request = new UpdateProfileRequest("Tên mới", OLD_EMAIL, null, null);
        Actor actor = Actor.customer(7L);
        log.input("actor = {}, request = {}", actor, request);

        service.updateProfile(actor, request);

        log.output("fullName = {}, email = {}", customer.getContact().fullName(), customer.getContact().email());
        assertEquals("Tên mới", customer.getContact().fullName());
        assertEquals(OLD_EMAIL, customer.getContact().email());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_updateProfile_InvalidNotifyBefore_086() {
        // Nhắc trước 20 phút: chỉ nhận 10, 15, 30.
        Customer customer = account(7L);
        when(customers.findById(7L)).thenReturn(Optional.of(customer));
        UpdateProfileRequest request = new UpdateProfileRequest("Lê C", null, 20, null);
        Actor actor = Actor.customer(7L);
        log.input("actor = {}, request = {}", actor, request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.updateProfile(actor, request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_NOTIFY_BEFORE", thrown.code());
        verify(customers, never()).save(any());
    }

    // Viewpoint: VP2
    @Test
    public void test_updateProfile_InvalidEmail_087() {
        // Email sai định dạng.
        when(customers.findById(7L)).thenReturn(Optional.of(account(7L)));
        UpdateProfileRequest request = new UpdateProfileRequest("Lê C", "khong-phai-email", null, null);
        Actor actor = Actor.customer(7L);
        log.input("actor = {}, request = {}", actor, request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.updateProfile(actor, request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CONTACT", thrown.code());
    }

    // Viewpoint: VP2
    @Test
    public void test_updateProfile_FullName3Byte_088() {
        // Họ tên toàn ký tự 3 byte vẫn lưu đúng.
        Customer customer = account(7L);
        when(customers.findById(7L)).thenReturn(Optional.of(customer));
        UpdateProfileRequest request = new UpdateProfileRequest(TEXT_3_BYTE, null, null, null);
        Actor actor = Actor.customer(7L);
        log.input("actor = {}, request = {}", actor, request);

        service.updateProfile(actor, request);

        log.output("fullName = {}", customer.getContact().fullName());
        assertEquals(TEXT_3_BYTE, customer.getContact().fullName());
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_updateProfile_StaffActor_089() {
        // Nhân viên không dùng chức năng hồ sơ khách.
        UpdateProfileRequest request = new UpdateProfileRequest("Lê C", null, null, null);
        Actor actor = Actor.staff(5L, StaffRole.RECEPTIONIST);
        log.input("actor = {}, request = {}", actor, request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.updateProfile(actor, request));

        log.output("code = {}", thrown.code());
        assertEquals("CUSTOMER_ONLY", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_updateProfile_CustomerNotFound_090() {
        // Token khách nhưng tài khoản đã bị xóa.
        UpdateProfileRequest request = new UpdateProfileRequest("Lê C", null, null, null);
        Actor actor = Actor.customer(7L);
        log.input("actor = {}, request = {}", actor, request);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.updateProfile(actor, request));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // ---------- changePassword ----------

    // Viewpoint: VP3
    @Test
    public void test_changePassword_CorrectCurrent_091() {
        // Đúng mật khẩu hiện tại: lưu mật khẩu mới (đã băm).
        Customer customer = account(7L);
        when(customers.findById(7L)).thenReturn(Optional.of(customer));
        ChangePasswordRequest request = new ChangePasswordRequest(PASSWORD, NEW_PASSWORD);
        Actor actor = Actor.customer(7L);
        log.input("actor = {}, request = {}", actor, request);

        service.changePassword(actor, request);

        boolean newMatches = encoder.matches(NEW_PASSWORD, customer.getPasswordHash());
        boolean oldMatches = encoder.matches(PASSWORD, customer.getPasswordHash());
        log.output("newMatches = {}, oldMatches = {}", newMatches, oldMatches);
        assertTrue(newMatches);
        assertFalse(oldMatches);
        verify(customers).save(customer);
    }

    // Viewpoint: VP3
    @Test
    public void test_changePassword_WrongCurrent_092() {
        // Sai mật khẩu hiện tại.
        Customer customer = account(7L);
        String hash = customer.getPasswordHash();
        when(customers.findById(7L)).thenReturn(Optional.of(customer));
        ChangePasswordRequest request = new ChangePasswordRequest("saimatkhau", NEW_PASSWORD);
        Actor actor = Actor.customer(7L);
        log.input("actor = {}, request = {}", actor, request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.changePassword(actor, request));

        log.output("code = {}, hashUnchanged = {}", thrown.code(), hash.equals(customer.getPasswordHash()));
        assertEquals("INVALID_CURRENT_PASSWORD", thrown.code());
        assertEquals(hash, customer.getPasswordHash());
    }

    // Viewpoint: VP3
    @Test
    public void test_changePassword_SameAsCurrent_093() {
        // Mật khẩu mới trùng mật khẩu hiện tại.
        when(customers.findById(7L)).thenReturn(Optional.of(account(7L)));
        ChangePasswordRequest request = new ChangePasswordRequest(PASSWORD, PASSWORD);
        Actor actor = Actor.customer(7L);
        log.input("actor = {}, request = {}", actor, request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.changePassword(actor, request));

        log.output("code = {}", thrown.code());
        assertEquals("PASSWORD_UNCHANGED", thrown.code());
        verify(customers, never()).save(any());
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_changePassword_GuestActor_094() {
        // Khách vãng lai không có tài khoản.
        ChangePasswordRequest request = new ChangePasswordRequest(PASSWORD, NEW_PASSWORD);
        Actor actor = Actor.GUEST;
        log.input("actor = {}, request = {}", actor, request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.changePassword(actor, request));

        log.output("code = {}", thrown.code());
        assertEquals("CUSTOMER_ONLY", thrown.code());
    }

    // Viewpoint: VP2
    @Test
    public void test_changePassword_NewPassword3Byte_095() {
        // Mật khẩu mới gồm ký tự 1, 2, 3 byte.
        Customer customer = account(7L);
        when(customers.findById(7L)).thenReturn(Optional.of(customer));
        String newPassword = TEXT_1_BYTE + TEXT_2_BYTE + TEXT_3_BYTE;
        ChangePasswordRequest request = new ChangePasswordRequest(PASSWORD, newPassword);
        Actor actor = Actor.customer(7L);
        log.input("actor = {}, request = {}", actor, request);

        service.changePassword(actor, request);

        boolean matches = encoder.matches(newPassword, customer.getPasswordHash());
        log.output("matches = {}", matches);
        assertTrue(matches);
    }

    // ---------- resetPassword ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_resetPassword_UnverifiedPhone_096() {
        // OTP đúng: đặt mật khẩu mới, đánh dấu SĐT đã xác thực, đăng nhập luôn.
        Customer customer = account(7L);
        when(customers.findByPhone(PHONE)).thenReturn(Optional.of(customer));
        ResetPasswordRequest request = new ResetPasswordRequest(PHONE, OTP, NEW_PASSWORD);
        log.input("request = {}", request);

        AuthResponse response = service.resetPassword(request);

        boolean matches = encoder.matches(NEW_PASSWORD, customer.getPasswordHash());
        log.output("matches = {}, phoneVerifiedAt = {}, accessToken = {}", matches, customer.getPhoneVerifiedAt(),
                response.accessToken());
        assertTrue(matches);
        assertEquals(NOW, customer.getPhoneVerifiedAt());
        assertEquals("jwt", response.accessToken());
        verify(otpService).verify(PHONE, OTP);
    }

    // Viewpoint: VP3
    @Test
    public void test_resetPassword_AlreadyVerifiedKeepsTimestamp_097() {
        // SĐT đã xác thực từ trước: giữ nguyên thời điểm xác thực.
        Customer customer = account(7L);
        customer.setPhoneVerifiedAt(NOW.minusDays(3));
        when(customers.findByPhone(PHONE)).thenReturn(Optional.of(customer));
        ResetPasswordRequest request = new ResetPasswordRequest(PHONE, OTP, NEW_PASSWORD);
        log.input("request = {}", request);

        service.resetPassword(request);

        log.output("phoneVerifiedAt = {}", customer.getPhoneVerifiedAt());
        assertEquals(NOW.minusDays(3), customer.getPhoneVerifiedAt());
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_resetPassword_GuestWithoutAccount_098() {
        // SĐT chỉ là khách vãng lai (chưa có mật khẩu): không cho đặt lại, không tốn OTP.
        when(customers.findByPhone(PHONE)).thenReturn(Optional.of(guest(7L, null)));
        ResetPasswordRequest request = new ResetPasswordRequest(PHONE, OTP, NEW_PASSWORD);
        log.input("request = {}", request);

        AuthenticationFailedException thrown = assertThrows(AuthenticationFailedException.class, () -> service.resetPassword(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CREDENTIALS", thrown.code());
        verify(otpService, never()).verify(anyString(), anyString());
    }

    // Viewpoint: VP7
    @Test
    public void test_resetPassword_PhoneNotExisted_099() {
        // SĐT chưa có trong hệ thống.
        ResetPasswordRequest request = new ResetPasswordRequest(PHONE, OTP, NEW_PASSWORD);
        log.input("request = {}", request);

        AuthenticationFailedException thrown = assertThrows(AuthenticationFailedException.class, () -> service.resetPassword(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CREDENTIALS", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_resetPassword_WrongOtp_100() {
        // OTP sai: lỗi của OtpService được ném tiếp, mật khẩu giữ nguyên.
        Customer customer = account(7L);
        String hash = customer.getPasswordHash();
        when(customers.findByPhone(PHONE)).thenReturn(Optional.of(customer));
        doThrow(new DomainException("OTP_INVALID", "sai mã")).when(otpService).verify(PHONE, "000000");
        ResetPasswordRequest request = new ResetPasswordRequest(PHONE, "000000", NEW_PASSWORD);
        log.input("request = {}", request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.resetPassword(request));

        log.output("code = {}, hashUnchanged = {}", thrown.code(), hash.equals(customer.getPasswordHash()));
        assertEquals("OTP_INVALID", thrown.code());
        assertEquals(hash, customer.getPasswordHash());
        verify(customers, never()).save(any());
    }

    // ---------- dữ liệu dùng chung ----------

    private static RegisterPhoneRequest phoneRequest(String phone, String fullName, String password, String email) {
        return new RegisterPhoneRequest(phone, OTP, fullName, password, email);
    }

    private static RegisterEmailRequest emailRequest(String email, String phone, String fullName, String password) {
        return new RegisterEmailRequest(email, phone, fullName, password);
    }

    /** Khách được lưu trong lần gọi {@code customers.save} duy nhất của test. */
    private Customer savedCustomer() {
        org.mockito.ArgumentCaptor<Customer> captor = org.mockito.ArgumentCaptor.forClass(Customer.class);
        verify(customers).save(captor.capture());
        return captor.getValue();
    }

    private static Customer guest(Long id, String email) {
        Customer c = new Customer();
        c.setId(id);
        c.setContact(new ContactInfo("Khách cũ", PHONE, email));
        c.setCreatedAt(NOW.minusDays(10));
        return c;
    }

    private Customer account(Long id) {
        Customer c = guest(id, OLD_EMAIL);
        c.setPasswordHash(encoder.encode(PASSWORD));
        return c;
    }

    private Staff staffMember(Long id, Branch branch, StaffStatus status) {
        Staff member = new Staff(branch, "Lan", STAFF_EMAIL, encoder.encode(STAFF_PASSWORD), StaffRole.MANAGER, 0);
        member.setId(id);
        member.setStatus(status);
        return member;
    }
}
