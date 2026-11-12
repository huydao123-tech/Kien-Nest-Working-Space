package com.cowork.business.service;

import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.dataaccess.entity.Customer;
import com.cowork.dataaccess.enums.NotificationChannel;
import com.cowork.dataaccess.repository.CustomerRepository;
import com.cowork.dataaccess.valueobject.ContactInfo;
import com.cowork.dto.PeopleDtos.CustomerRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Khách hàng, định danh bằng số điện thoại. */
@Service
@Transactional
public class CustomerService {

    private static final Pattern VN_PHONE = Pattern.compile("^(0|\\+84)\\d{9}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    /** Giá trị cho phép của cột notify_before_minutes (ck_customer_notify_before). */
    private static final Set<Integer> NOTIFY_BEFORE = Set.of(10, 15, 30);

    private final CustomerRepository customers;
    private final Clock clock;

    public CustomerService(CustomerRepository customers, Clock clock) {
        this.customers = customers;
        this.clock = clock;
    }

    /**
     * Tìm khách theo số điện thoại; chưa có thì tạo mới (khách vãng lai, FR-AUTH-01).
     * Khách vãng lai cũ được cập nhật họ tên / email theo lần đặt mới nhất; email bỏ trống thì giữ email cũ.
     * Khách đã có tài khoản (UC01) thì giữ nguyên hồ sơ: người đặt hộ bằng SĐT đó không sửa được hồ sơ của họ.
     */
    public Customer registerOrUpdate(ContactInfo contact) {
        validate(contact);
        return customers.findByPhone(contact.phone())
                .map(existing -> {
                    if (existing.getPasswordHash() == null) {
                        String email = contact.hasEmail() ? contact.email() : existing.getContact().email();
                        existing.setContact(new ContactInfo(contact.fullName(), contact.phone(), email));
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    Customer customer = new Customer();
                    customer.setContact(contact);
                    customer.setCreatedAt(LocalDateTime.now(clock));
                    return customers.save(customer);
                });
    }

    @Transactional(readOnly = true)
    public Customer get(Long id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Khách hàng", id));
    }

    // ------------------------------------------------------------------
    // Quản trị khách hàng (nhân viên) — ADMIN, MANAGER, RECEPTIONIST
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Customer> list() {
        return customers.findAllByOrderByIdAsc();
    }

    /** Lễ tân tạo hồ sơ khách tại quầy (khách vãng lai, chưa có mật khẩu). */
    public Customer create(CustomerRequest request) {
        ContactInfo contact = toContact(request);
        validate(contact);
        if (customers.findByPhone(contact.phone()).isPresent()) {
            throw new ConflictException("PHONE_ALREADY_USED", "Số điện thoại đã thuộc về một khách khác");
        }
        Customer customer = new Customer();
        customer.setContact(contact);
        customer.setCreatedAt(LocalDateTime.now(clock));
        applyPreferences(customer, request.notifyBeforeMinutes(), request.preferredChannel());
        return customers.save(customer);
    }

    /** Đổi SĐT thì SĐT mới phải chưa có ai dùng và phải xác thực lại (phone_verified_at = rỗng). */
    public Customer update(Long id, CustomerRequest request) {
        Customer customer = get(id);
        ContactInfo contact = toContact(request);
        validate(contact);
        if (!contact.phone().equals(customer.getContact().phone())) {
            if (customers.findByPhone(contact.phone()).isPresent()) {
                throw new ConflictException("PHONE_ALREADY_USED", "Số điện thoại đã thuộc về một khách khác");
            }
            customer.setPhoneVerifiedAt(null);
        }
        if (customer.getPasswordHash() != null && contact.hasEmail()) {
            customers.findAccountByEmail(contact.email())
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new ConflictException("EMAIL_ALREADY_USED", "Email đã được dùng cho một tài khoản khác");
                    });
        }
        customer.setContact(contact);
        applyPreferences(customer, request.notifyBeforeMinutes(), request.preferredChannel());
        return customers.save(customer);
    }

    /** Khách đã có đơn thì không xóa được (ràng buộc khóa ngoại → 409 DATA_CONFLICT). */
    public void delete(Long id) {
        customers.delete(get(id));
        customers.flush();
    }

    // ------------------------------------------------------------------
    // Kiểm tra dữ liệu, dùng chung với AuthService
    // ------------------------------------------------------------------

    static void validate(ContactInfo contact) {
        if (contact.fullName() == null || contact.fullName().isBlank()) {
            throw new DomainException("INVALID_CONTACT", "Họ tên là bắt buộc");
        }
        requireValidPhone(contact.phone());
        if (contact.hasEmail() && !EMAIL.matcher(contact.email()).matches()) {
            throw new DomainException("INVALID_CONTACT", "Email không hợp lệ");
        }
    }

    static void requireValidPhone(String phone) {
        if (phone == null || !VN_PHONE.matcher(phone).matches()) {
            throw new DomainException("INVALID_CONTACT", "Số điện thoại không hợp lệ");
        }
    }

    private static ContactInfo toContact(CustomerRequest request) {
        String email = request.email() == null || request.email().isBlank() ? null : request.email().trim();
        return new ContactInfo(request.fullName() == null ? null : request.fullName().trim(), request.phone(), email);
    }

    /** Tùy chọn nhận thông báo; trường rỗng thì giữ nguyên. Dùng chung với AuthService (khách tự sửa hồ sơ, FR-AUTH-05). */
    static void applyPreferences(Customer customer, Integer notifyBeforeMinutes, NotificationChannel preferredChannel) {
        if (notifyBeforeMinutes != null) {
            if (!NOTIFY_BEFORE.contains(notifyBeforeMinutes)) {
                throw new DomainException("INVALID_NOTIFY_BEFORE", "Thời gian nhắc trước chỉ nhận 10, 15 hoặc 30 phút");
            }
            customer.setNotifyBeforeMinutes(notifyBeforeMinutes);
        }
        if (preferredChannel != null) {
            customer.setPreferredChannel(preferredChannel);
        }
    }
}
