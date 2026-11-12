package com.cowork.dto;

import com.cowork.dataaccess.entity.Customer;
import com.cowork.dataaccess.entity.Staff;
import com.cowork.dataaccess.enums.NotificationChannel;
import com.cowork.dataaccess.enums.StaffRole;
import com.cowork.dto.CommerceDtos.MembershipSummary;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDateTime;

/** Đăng ký, đăng nhập, hồ sơ người dùng (UC01, UC23). */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record OtpRequest(@NotBlank String phone) {
    }

    public record OtpSentResponse(String phone, LocalDateTime expiresAt) {
    }

    /** FR-AUTH-02: đăng ký bằng SĐT đã xác thực OTP. Email không bắt buộc. */
    public record RegisterPhoneRequest(@NotBlank String phone,
                                       @NotBlank String otp,
                                       @NotBlank @Size(max = 255) String fullName,
                                       @NotBlank @Size(min = 8, max = 72) String password,
                                       @Email @Size(max = 255) String email) {
    }

    /** FR-AUTH-02: đăng ký bằng email. Bảng khách định danh bằng SĐT nên vẫn phải nhập SĐT (chưa xác thực). */
    public record RegisterEmailRequest(@NotBlank @Email @Size(max = 255) String email,
                                       @NotBlank String phone,
                                       @NotBlank @Size(max = 255) String fullName,
                                       @NotBlank @Size(min = 8, max = 72) String password) {
    }

    /** {@code login} là số điện thoại hoặc email. */
    public record LoginRequest(@NotBlank String login, @NotBlank String password) {
    }

    public record OtpLoginRequest(@NotBlank String phone, @NotBlank String otp) {
    }

    public record StaffLoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    /**
     * FR-AUTH-05: khách sửa hồ sơ. Số điện thoại là định danh tài khoản nên không sửa ở đây.
     * {@code email} bỏ trống = xóa email; {@code notifyBeforeMinutes}, {@code preferredChannel} bỏ trống = giữ nguyên.
     */
    public record UpdateProfileRequest(@NotBlank @Size(max = 255) String fullName,
                                       @Email @Size(max = 255) String email,
                                       Integer notifyBeforeMinutes,
                                       NotificationChannel preferredChannel) {
    }

    /** FR-AUTH-05: đổi mật khẩu khi đang đăng nhập. */
    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    /** FR-AUTH-05: quên mật khẩu — xác thực bằng OTP gửi tới SĐT của tài khoản ({@code POST /api/auth/otp}). */
    public record ResetPasswordRequest(@NotBlank String phone,
                                       @NotBlank String otp,
                                       @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, ProfileResponse user) {
    }

    /**
     * Hồ sơ người đang đăng nhập: khách ({@code type = CUSTOMER}) hoặc nhân viên ({@code type = STAFF}).
     * {@code notifyBeforeMinutes}, {@code preferredChannel} chỉ có với khách. {@code membership} (FR-AUTH-05) chỉ có ở
     * trang hồ sơ của khách ({@code GET/PUT /api/auth/me}); các response đăng nhập để rỗng.
     */
    public record ProfileResponse(Actor.Type type,
                                  Long id,
                                  String fullName,
                                  String phone,
                                  String email,
                                  boolean phoneVerified,
                                  StaffRole role,
                                  Long branchId,
                                  Integer notifyBeforeMinutes,
                                  NotificationChannel preferredChannel,
                                  MembershipSummary membership) {

        public static ProfileResponse from(Customer c) {
            return from(c, null);
        }

        public static ProfileResponse from(Customer c, MembershipSummary membership) {
            return new ProfileResponse(Actor.Type.CUSTOMER, c.getId(), c.getContact().fullName(), c.getContact().phone(),
                    c.getContact().email(), c.getPhoneVerifiedAt() != null, null, null,
                    c.getNotifyBeforeMinutes(), c.getPreferredChannel(), membership);
        }

        public static ProfileResponse from(Staff s) {
            return new ProfileResponse(Actor.Type.STAFF, s.getId(), s.getFullName(), null, s.getEmail(), false,
                    s.getRole(), s.getBranch() == null ? null : s.getBranch().getId(), null, null, null);
        }
    }
}
