package com.cowork.business.service;

import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.dataaccess.entity.Customer;
import com.cowork.dataaccess.enums.NotificationChannel;
import com.cowork.dataaccess.repository.CustomerRepository;
import com.cowork.dataaccess.valueobject.ContactInfo;
import com.cowork.dto.PeopleDtos.CustomerRequest;
import com.cowork.support.MutableClock;
import com.cowork.support.TestCaseLogRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** FR-AUTH-01, UC14: khách định danh bằng SĐT, quản trị hồ sơ khách. Viết theo {@code .claude/rules/unit-test.md}. */
public class CustomerServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 8, 0);
    private static final String PHONE = "0911222333";
    private static final String OTHER_PHONE = "0911222444";
    private static final String EMAIL = "c@example.com";

    @Rule
    public final TestCaseLogRule log = new TestCaseLogRule();

    private CustomerRepository customers;
    private CustomerService service;

    @Before
    public void setUp() {
        MutableClock clock = new MutableClock(ZoneId.of("Asia/Ho_Chi_Minh"));
        clock.set(NOW);
        customers = mock(CustomerRepository.class);
        service = new CustomerService(customers, clock);
        when(customers.save(any(Customer.class))).thenAnswer(call -> call.getArgument(0));
    }

    // ---------- registerOrUpdate ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_registerOrUpdate_NewPhone_001() {
        // SĐT mới: tạo khách vãng lai.
        ContactInfo contact = new ContactInfo("Lê C", PHONE, null);
        log.input("contact = {}", contact);

        Customer customer = service.registerOrUpdate(contact);

        log.output("contact = {}, createdAt = {}", customer.getContact(), customer.getCreatedAt());
        assertEquals(contact, customer.getContact());
        assertEquals(NOW, customer.getCreatedAt());
        verify(customers).save(customer);
    }

    // Viewpoint: VP3
    @Test
    public void test_registerOrUpdate_GuestUpdatesNameAndEmail_002() {
        // Khách vãng lai cũ: cập nhật tên và email theo lần đặt mới.
        Customer existing = customer(1L, "Tên cũ", PHONE, "old@example.com", null);
        ContactInfo contact = new ContactInfo("Tên mới", PHONE, EMAIL);
        log.input("contact = {}", contact);

        Customer customer = service.registerOrUpdate(contact);

        log.output("contact = {}", customer.getContact());
        assertEquals(new ContactInfo("Tên mới", PHONE, EMAIL), customer.getContact());
        assertSame(existing, customer);
    }

    // Viewpoint: VP2, VP3
    @Test
    public void test_registerOrUpdate_GuestBlankEmailKeepsOld_003() {
        // Email bỏ trống (chỉ khoảng trắng): giữ email cũ.
        customer(1L, "Tên cũ", PHONE, "old@example.com", null);
        ContactInfo contact = new ContactInfo("Tên mới", PHONE, "  ");
        log.input("contact = {}", contact);

        Customer customer = service.registerOrUpdate(contact);

        log.output("email = {}", customer.getContact().email());
        assertEquals("old@example.com", customer.getContact().email());
    }

    // Viewpoint: VP3
    @Test
    public void test_registerOrUpdate_AccountKeepsProfile_004() {
        // SĐT đã có tài khoản: người đặt hộ không sửa được hồ sơ.
        customer(1L, "Chủ tài khoản", PHONE, EMAIL, "hash");
        ContactInfo contact = new ContactInfo("Người đặt hộ", PHONE, "khac@example.com");
        log.input("contact = {}", contact);

        Customer customer = service.registerOrUpdate(contact);

        log.output("contact = {}", customer.getContact());
        assertEquals(new ContactInfo("Chủ tài khoản", PHONE, EMAIL), customer.getContact());
        verify(customers, never()).save(any());
    }

    // ---------- validate (qua registerOrUpdate) ----------

    // Viewpoint: VP2
    @Test
    public void test_registerOrUpdate_BlankName_005() {
        // Họ tên rỗng.
        ContactInfo contact = new ContactInfo(" ", PHONE, null);
        log.input("contact = {}", contact);

        DomainException thrown = assertThrows(DomainException.class, () -> service.registerOrUpdate(contact));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CONTACT", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_registerOrUpdate_NullName_006() {
        // Họ tên null.
        ContactInfo contact = new ContactInfo(null, PHONE, null);
        log.input("contact = {}", contact);

        DomainException thrown = assertThrows(DomainException.class, () -> service.registerOrUpdate(contact));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CONTACT", thrown.code());
    }

    // Viewpoint: VP1, VP2
    @Test
    public void test_registerOrUpdate_PhoneWithCountryCode_007() {
        // SĐT dạng +84 và 9 chữ số: hợp lệ.
        ContactInfo contact = new ContactInfo("Lê C", "+84911222333", null);
        log.input("contact = {}", contact);

        Customer customer = service.registerOrUpdate(contact);

        log.output("phone = {}", customer.getContact().phone());
        assertEquals("+84911222333", customer.getContact().phone());
    }

    // Viewpoint: VP1
    @Test
    public void test_registerOrUpdate_PhoneNineDigits_008() {
        // SĐT thiếu 1 chữ số (cận dưới).
        ContactInfo contact = new ContactInfo("Lê C", "091122233", null);
        log.input("contact = {}", contact);

        DomainException thrown = assertThrows(DomainException.class, () -> service.registerOrUpdate(contact));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CONTACT", thrown.code());
    }

    // Viewpoint: VP1
    @Test
    public void test_registerOrUpdate_PhoneElevenDigits_009() {
        // SĐT thừa 1 chữ số (cận trên).
        ContactInfo contact = new ContactInfo("Lê C", "09112223334", null);
        log.input("contact = {}", contact);

        DomainException thrown = assertThrows(DomainException.class, () -> service.registerOrUpdate(contact));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CONTACT", thrown.code());
    }

    // Viewpoint: VP2
    @Test
    public void test_registerOrUpdate_Phone3Byte_010() {
        // SĐT có ký tự 3 byte.
        ContactInfo contact = new ContactInfo("Lê C", "09112223ệ3", null);
        log.input("contact = {}", contact);

        DomainException thrown = assertThrows(DomainException.class, () -> service.registerOrUpdate(contact));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CONTACT", thrown.code());
    }

    // Viewpoint: VP2
    @Test
    public void test_registerOrUpdate_InvalidEmail_011() {
        // Email thiếu tên miền.
        ContactInfo contact = new ContactInfo("Lê C", PHONE, "c@example");
        log.input("contact = {}", contact);

        DomainException thrown = assertThrows(DomainException.class, () -> service.registerOrUpdate(contact));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CONTACT", thrown.code());
    }

    // Viewpoint: VP2
    @Test
    public void test_registerOrUpdate_Name2And3Byte_012() {
        // Họ tên ký tự 2 và 3 byte.
        ContactInfo contact = new ContactInfo("àêô ệặữ", PHONE, null);
        log.input("contact = {}", contact);

        Customer customer = service.registerOrUpdate(contact);

        log.output("fullName = {}", customer.getContact().fullName());
        assertEquals("àêô ệặữ", customer.getContact().fullName());
    }

    // ---------- get ----------

    // Viewpoint: VP7
    @Test
    public void test_get_Found_013() {
        // Có khách.
        Customer existing = customer(1L, "Lê C", PHONE, null, null);
        log.input("id = {}", 1L);

        Customer customer = service.get(1L);

        log.output("id = {}", customer.getId());
        assertSame(existing, customer);
    }

    // Viewpoint: VP7
    @Test
    public void test_get_NotFound_014() {
        // Không có khách.
        log.input("id = {}", 99L);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.get(99L));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // ---------- create ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_create_WithPreferences_015() {
        // Lễ tân tạo khách: cắt khoảng trắng, email rỗng → null, lưu tùy chọn thông báo.
        CustomerRequest request = new CustomerRequest("  Lê C  ", PHONE, "", 10, NotificationChannel.ZALO);
        log.input("request = {}", request);

        Customer customer = service.create(request);

        log.output("contact = {}, notifyBeforeMinutes = {}, preferredChannel = {}, createdAt = {}", customer.getContact(),
                customer.getNotifyBeforeMinutes(), customer.getPreferredChannel(), customer.getCreatedAt());
        assertEquals(new ContactInfo("Lê C", PHONE, null), customer.getContact());
        assertEquals(10, customer.getNotifyBeforeMinutes());
        assertEquals(NotificationChannel.ZALO, customer.getPreferredChannel());
        assertEquals(NOW, customer.getCreatedAt());
    }

    // Viewpoint: VP3
    @Test
    public void test_create_PhoneUsed_016() {
        // SĐT đã thuộc khách khác.
        customer(1L, "Khác", PHONE, null, null);
        CustomerRequest request = new CustomerRequest("Lê C", PHONE, null, null, null);
        log.input("request = {}", request);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.create(request));

        log.output("code = {}", thrown.code());
        assertEquals("PHONE_ALREADY_USED", thrown.code());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_create_NotifyBeforeNotAllowed_017() {
        // Nhắc trước 31 phút (ngoài 10 / 15 / 30).
        CustomerRequest request = new CustomerRequest("Lê C", PHONE, null, 31, null);
        log.input("request = {}", request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.create(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_NOTIFY_BEFORE", thrown.code());
    }

    // Viewpoint: VP1, VP7
    @Test
    public void test_create_NullPreferencesUseDefaults_018() {
        // Không chọn tùy chọn: giữ mặc định 15 phút, SMS.
        CustomerRequest request = new CustomerRequest("Lê C", PHONE, null, null, null);
        log.input("request = {}", request);

        Customer customer = service.create(request);

        log.output("notifyBeforeMinutes = {}, preferredChannel = {}", customer.getNotifyBeforeMinutes(), customer.getPreferredChannel());
        assertEquals(15, customer.getNotifyBeforeMinutes());
        assertEquals(NotificationChannel.SMS, customer.getPreferredChannel());
    }

    // Viewpoint: VP7
    @Test
    public void test_create_NullFullName_019() {
        // Họ tên null.
        CustomerRequest request = new CustomerRequest(null, PHONE, null, null, null);
        log.input("request = {}", request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.create(request));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_CONTACT", thrown.code());
    }

    // ---------- update ----------

    // Viewpoint: VP3
    @Test
    public void test_update_ChangePhone_020() {
        // Đổi sang SĐT chưa ai dùng: phải xác thực lại.
        Customer existing = customer(1L, "Lê C", PHONE, null, "hash");
        existing.setPhoneVerifiedAt(NOW.minusDays(1));
        when(customers.findById(1L)).thenReturn(Optional.of(existing));
        CustomerRequest request = new CustomerRequest("Lê C", OTHER_PHONE, null, null, null);
        log.input("id = {}, request = {}", 1L, request);

        Customer customer = service.update(1L, request);

        log.output("phone = {}, phoneVerifiedAt = {}", customer.getContact().phone(), customer.getPhoneVerifiedAt());
        assertEquals(OTHER_PHONE, customer.getContact().phone());
        assertNull(customer.getPhoneVerifiedAt());
    }

    // Viewpoint: VP3
    @Test
    public void test_update_PhoneUsedByOther_021() {
        // Đổi sang SĐT của khách khác.
        Customer existing = customer(1L, "Lê C", PHONE, null, null);
        when(customers.findById(1L)).thenReturn(Optional.of(existing));
        when(customers.findByPhone(OTHER_PHONE)).thenReturn(Optional.of(new Customer()));
        CustomerRequest request = new CustomerRequest("Lê C", OTHER_PHONE, null, null, null);
        log.input("id = {}, request = {}", 1L, request);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.update(1L, request));

        log.output("code = {}", thrown.code());
        assertEquals("PHONE_ALREADY_USED", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_update_SamePhoneKeepsVerification_022() {
        // Giữ SĐT: không mất trạng thái xác thực.
        Customer existing = customer(1L, "Lê C", PHONE, null, null);
        existing.setPhoneVerifiedAt(NOW.minusDays(1));
        CustomerRequest request = new CustomerRequest("Tên mới", PHONE, null, null, null);
        log.input("id = {}, request = {}", 1L, request);

        Customer customer = service.update(1L, request);

        log.output("fullName = {}, phoneVerifiedAt = {}", customer.getContact().fullName(), customer.getPhoneVerifiedAt());
        assertEquals("Tên mới", customer.getContact().fullName());
        assertEquals(NOW.minusDays(1), customer.getPhoneVerifiedAt());
    }

    // Viewpoint: VP3
    @Test
    public void test_update_AccountEmailUsedByOther_023() {
        // Khách có tài khoản đổi sang email đăng nhập của tài khoản khác.
        customer(1L, "Lê C", PHONE, null, "hash");
        Customer other = customer(2L, "Khác", OTHER_PHONE, EMAIL, "hash");
        when(customers.findAccountByEmail(EMAIL)).thenReturn(Optional.of(other));
        CustomerRequest request = new CustomerRequest("Lê C", PHONE, EMAIL, null, null);
        log.input("id = {}, request = {}", 1L, request);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.update(1L, request));

        log.output("code = {}", thrown.code());
        assertEquals("EMAIL_ALREADY_USED", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_update_AccountKeepsOwnEmail_024() {
        // Email trùng chính tài khoản: hợp lệ.
        Customer existing = customer(1L, "Lê C", PHONE, EMAIL, "hash");
        when(customers.findAccountByEmail(EMAIL)).thenReturn(Optional.of(existing));
        CustomerRequest request = new CustomerRequest("Lê C", PHONE, EMAIL, 30, NotificationChannel.EMAIL);
        log.input("id = {}, request = {}", 1L, request);

        Customer customer = service.update(1L, request);

        log.output("email = {}, notifyBeforeMinutes = {}", customer.getContact().email(), customer.getNotifyBeforeMinutes());
        assertEquals(EMAIL, customer.getContact().email());
        assertEquals(30, customer.getNotifyBeforeMinutes());
    }

    // Viewpoint: VP3
    @Test
    public void test_update_GuestEmailNotChecked_025() {
        // Khách vãng lai được trùng email với tài khoản khác (không kiểm tra).
        customer(1L, "Lê C", PHONE, null, null);
        CustomerRequest request = new CustomerRequest("Lê C", PHONE, EMAIL, null, null);
        log.input("id = {}, request = {}", 1L, request);

        Customer customer = service.update(1L, request);

        log.output("email = {}", customer.getContact().email());
        assertEquals(EMAIL, customer.getContact().email());
        verify(customers, never()).findAccountByEmail(any());
    }

    // Viewpoint: VP7
    @Test
    public void test_update_NotFound_026() {
        // Không có khách.
        CustomerRequest request = new CustomerRequest("Lê C", PHONE, null, null, null);
        log.input("id = {}, request = {}", 99L, request);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.update(99L, request));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // ---------- delete ----------

    // Viewpoint: VP7
    @Test
    public void test_delete_Found_027() {
        // Xóa và flush ngay để lỗi khóa ngoại báo ra trong lời gọi.
        Customer existing = customer(1L, "Lê C", PHONE, null, null);
        log.input("id = {}", 1L);

        service.delete(1L);

        log.output("deletedId = {}", existing.getId());
        assertEquals(Long.valueOf(1L), existing.getId());
        verify(customers).delete(existing);
        verify(customers).flush();
    }

    // Viewpoint: VP7
    @Test
    public void test_delete_NotFound_028() {
        // Không có khách.
        log.input("id = {}", 99L);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.delete(99L));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
        verify(customers, never()).delete(any());
    }

    /** Khách có sẵn, tìm được theo id và SĐT; {@code passwordHash} khác rỗng = có tài khoản. */
    private Customer customer(Long id, String name, String phone, String email, String passwordHash) {
        Customer c = new Customer();
        c.setId(id);
        c.setContact(new ContactInfo(name, phone, email));
        c.setCreatedAt(NOW.minusDays(10));
        c.setPasswordHash(passwordHash);
        when(customers.findById(id)).thenReturn(Optional.of(c));
        when(customers.findByPhone(phone)).thenReturn(Optional.of(c));
        return c;
    }
}
