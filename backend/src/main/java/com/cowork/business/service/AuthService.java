package com.cowork.business.service;

import com.cowork.business.exception.AuthenticationFailedException;
import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.business.service.TokenService.IssuedToken;
import com.cowork.dataaccess.entity.Customer;
import com.cowork.dataaccess.entity.Staff;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * UC01: khách đăng ký / đăng nhập (SĐT + OTP, hoặc email + mật khẩu). UC23: nhân viên đăng nhập bằng tài khoản nội bộ.
 * Đăng nhập thành công trả về access token JWT ({@link TokenService}).
 *
 * <p>FR-AUTH-03: khách vãng lai đã đặt chỗ bằng một SĐT, khi đăng ký bằng chính SĐT đó (đã xác thực OTP) thì dùng lại
 * đúng dòng {@code customer} cũ, nên mọi đơn cũ tự thuộc về tài khoản. Đăng ký bằng email (SĐT chưa xác thực) không được
 * nhận dòng khách cũ, để không ai chiếm được lịch sử đặt chỗ của người khác.
 */
@Service
@Transactional
public class AuthService {

    private static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";

    private final CustomerRepository customers;
    private final StaffRepository staff;
    private final OtpService otpService;
    private final TokenService tokens;
    private final PasswordEncoder passwordEncoder;
    private final MembershipService memberships;
    private final Clock clock;

    public AuthService(CustomerRepository customers, StaffRepository staff, OtpService otpService, TokenService tokens,
                       PasswordEncoder passwordEncoder, MembershipService memberships, Clock clock) {
        this.customers = customers;
        this.staff = staff;
        this.otpService = otpService;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.memberships = memberships;
        this.clock = clock;
    }

    /** FR-AUTH-02: đăng ký bằng SĐT + OTP. SĐT đã có đơn vãng lai thì nhận lại các đơn đó (FR-AUTH-03). */
    public AuthResponse registerByPhone(RegisterPhoneRequest request) {
        String email = blankToNull(request.email());
        ContactInfo contact = new ContactInfo(request.fullName().trim(), request.phone(), email);
        CustomerService.validate(contact);
        Optional<Customer> existing = customers.findByPhone(request.phone());
        if (existing.isPresent() && existing.get().getPasswordHash() != null) {
            throw new ConflictException("ACCOUNT_ALREADY_EXISTS", "Số điện thoại đã có tài khoản, vui lòng đăng nhập");
        }
        if (email != null) {
            requireEmailFree(email);
        }
        // Kiểm tra mọi điều kiện khác trước, OTP sau cùng: mã đúng thì bị đánh dấu đã dùng.
        otpService.verify(request.phone(), request.otp());

        LocalDateTime now = now();
        Customer customer = existing.orElseGet(() -> {
            Customer created = new Customer();
            created.setCreatedAt(now);
            return created;
        });
        String keptEmail = email != null ? email : (customer.getContact() == null ? null : customer.getContact().email());
        customer.setContact(new ContactInfo(contact.fullName(), contact.phone(), keptEmail));
        customer.setPasswordHash(passwordEncoder.encode(request.password()));
        customer.setPhoneVerifiedAt(now);
        return tokenFor(customers.save(customer));
    }

    /** FR-AUTH-02: đăng ký bằng email + mật khẩu; SĐT lưu kèm nhưng chưa xác thực. */
    public AuthResponse registerByEmail(RegisterEmailRequest request) {
        String email = request.email().trim();
        ContactInfo contact = new ContactInfo(request.fullName().trim(), request.phone(), email);
        CustomerService.validate(contact);
        requireEmailFree(email);
        Optional<Customer> existing = customers.findByPhone(request.phone());
        if (existing.isPresent()) {
            if (existing.get().getPasswordHash() != null) {
                throw new ConflictException("ACCOUNT_ALREADY_EXISTS", "Số điện thoại đã có tài khoản, vui lòng đăng nhập");
            }
            throw new ConflictException("PHONE_VERIFICATION_REQUIRED",
                    "Số điện thoại đã có lịch sử đặt chỗ, vui lòng đăng ký bằng SĐT + OTP để nhận lại các đơn cũ");
        }
        Customer customer = new Customer();
        customer.setContact(contact);
        customer.setCreatedAt(now());
        customer.setPasswordHash(passwordEncoder.encode(request.password()));
        return tokenFor(customers.save(customer));
    }

    /** UC01: đăng nhập bằng SĐT hoặc email + mật khẩu. Sai bất kỳ thông tin nào đều trả cùng một lỗi. */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String login = request.login().trim();
        Optional<Customer> account = login.contains("@")
                ? customers.findAccountByEmail(login)
                : customers.findByPhone(login).filter(c -> c.getPasswordHash() != null);
        Customer customer = account
                .filter(c -> passwordEncoder.matches(request.password(), c.getPasswordHash()))
                .orElseThrow(() -> new AuthenticationFailedException(INVALID_CREDENTIALS, "Sai tài khoản hoặc mật khẩu"));
        return tokenFor(customer);
    }

    /** UC01: đăng nhập bằng SĐT + OTP (chỉ với SĐT đã có tài khoản). Đăng nhập được = SĐT đã xác thực. */
    public AuthResponse loginWithOtp(OtpLoginRequest request) {
        Customer customer = customers.findByPhone(request.phone())
                .filter(c -> c.getPasswordHash() != null)
                .orElseThrow(() -> new AuthenticationFailedException(INVALID_CREDENTIALS, "Số điện thoại chưa có tài khoản"));
        otpService.verify(request.phone(), request.otp());
        if (customer.getPhoneVerifiedAt() == null) {
            customer.setPhoneVerifiedAt(now());
        }
        return tokenFor(customer);
    }

    /** UC23: nhân viên đăng nhập bằng email nội bộ. Tài khoản bị khóa không đăng nhập được. */
    @Transactional(readOnly = true)
    public AuthResponse staffLogin(StaffLoginRequest request) {
        Staff member = staff.findByEmailIgnoreCase(request.email().trim())
                .filter(s -> passwordEncoder.matches(request.password(), s.getPasswordHash()))
                .orElseThrow(() -> new AuthenticationFailedException(INVALID_CREDENTIALS, "Sai email hoặc mật khẩu"));
        if (member.getStatus() != StaffStatus.ACTIVE) {
            throw new AuthenticationFailedException("ACCOUNT_LOCKED", "Tài khoản nhân viên đang bị khóa");
        }
        Actor actor = Actor.staff(member.getId(), member.getRole());
        Long branchId = member.getBranch() == null ? null : member.getBranch().getId();
        IssuedToken token = tokens.issue(actor, member.getFullName(), branchId);
        return new AuthResponse(token.value(), "Bearer", token.expiresAt(), ProfileResponse.from(member));
    }

    /** Hồ sơ của người đang đăng nhập. Khách có thêm số phút gói thành viên còn lại (FR-AUTH-05). */
    @Transactional(readOnly = true)
    public ProfileResponse profile(Actor actor) {
        if (actor.isStaff()) {
            return ProfileResponse.from(staff.findById(actor.id()).orElseThrow(() -> new NotFoundException("Nhân viên", actor.id())));
        }
        Customer customer = customers.findById(actor.id()).orElseThrow(() -> new NotFoundException("Khách hàng", actor.id()));
        return ProfileResponse.from(customer, memberships.summaryOf(customer.getId()));
    }

    /**
     * FR-AUTH-05: khách sửa họ tên, email, tùy chọn nhận thông báo. Không đổi SĐT (định danh tài khoản).
     * Email mới không được trùng email đăng nhập của tài khoản khác.
     */
    public ProfileResponse updateProfile(Actor actor, UpdateProfileRequest request) {
        Customer customer = requireAccount(actor);
        String email = blankToNull(request.email());
        ContactInfo contact = new ContactInfo(request.fullName().trim(), customer.getContact().phone(), email);
        CustomerService.validate(contact);
        if (email != null) {
            customers.findAccountByEmail(email)
                    .filter(other -> !other.getId().equals(customer.getId()))
                    .ifPresent(other -> {
                        throw new ConflictException("EMAIL_ALREADY_USED", "Email đã được dùng cho một tài khoản khác");
                    });
        }
        customer.setContact(contact);
        CustomerService.applyPreferences(customer, request.notifyBeforeMinutes(), request.preferredChannel());
        Customer saved = customers.save(customer);
        return ProfileResponse.from(saved, memberships.summaryOf(saved.getId()));
    }

    /** FR-AUTH-05: đổi mật khẩu — phải nhập đúng mật khẩu hiện tại, mật khẩu mới phải khác mật khẩu cũ. */
    public void changePassword(Actor actor, ChangePasswordRequest request) {
        Customer customer = requireAccount(actor);
        if (!passwordEncoder.matches(request.currentPassword(), customer.getPasswordHash())) {
            throw new DomainException("INVALID_CURRENT_PASSWORD", "Mật khẩu hiện tại không đúng");
        }
        if (passwordEncoder.matches(request.newPassword(), customer.getPasswordHash())) {
            throw new DomainException("PASSWORD_UNCHANGED", "Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        customer.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        customers.save(customer);
    }

    /**
     * FR-AUTH-05: quên mật khẩu — xác thực bằng OTP gửi tới SĐT của tài khoản rồi đặt mật khẩu mới, đăng nhập luôn.
     * Đặt lại được = SĐT đã xác thực.
     */
    public AuthResponse resetPassword(ResetPasswordRequest request) {
        Customer customer = customers.findByPhone(request.phone())
                .filter(c -> c.getPasswordHash() != null)
                .orElseThrow(() -> new AuthenticationFailedException(INVALID_CREDENTIALS, "Số điện thoại chưa có tài khoản"));
        otpService.verify(request.phone(), request.otp());
        customer.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        if (customer.getPhoneVerifiedAt() == null) {
            customer.setPhoneVerifiedAt(now());
        }
        return tokenFor(customers.save(customer));
    }

    /** Khách có tài khoản đang đăng nhập; nhân viên / khách vãng lai không dùng được các chức năng hồ sơ khách. */
    private Customer requireAccount(Actor actor) {
        if (!actor.isCustomer()) {
            throw new DomainException("CUSTOMER_ONLY", "Chức năng chỉ dành cho khách có tài khoản");
        }
        return customers.findById(actor.id()).orElseThrow(() -> new NotFoundException("Khách hàng", actor.id()));
    }

    private void requireEmailFree(String email) {
        if (customers.findAccountByEmail(email).isPresent()) {
            throw new ConflictException("EMAIL_ALREADY_USED", "Email đã được dùng cho một tài khoản khác");
        }
    }

    private AuthResponse tokenFor(Customer customer) {
        IssuedToken token = tokens.issue(Actor.customer(customer.getId()), customer.getContact().fullName(), null);
        return new AuthResponse(token.value(), "Bearer", token.expiresAt(), ProfileResponse.from(customer));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
