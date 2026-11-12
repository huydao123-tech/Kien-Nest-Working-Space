package com.cowork.dto;

import com.cowork.dataaccess.entity.Customer;
import com.cowork.dataaccess.entity.Staff;
import com.cowork.dataaccess.enums.NotificationChannel;
import com.cowork.dataaccess.enums.StaffRole;
import com.cowork.dataaccess.enums.StaffStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Quản trị nhân viên ({@code staff}) và khách hàng ({@code customer}). */
public final class PeopleDtos {

    private PeopleDtos() {
    }

    /** Tạo nhân viên: bắt buộc mật khẩu ban đầu. Sửa nhân viên: {@code password} bỏ trống = giữ mật khẩu cũ. */
    public record StaffRequest(@NotBlank @Size(max = 255) String fullName,
                               @NotBlank @Email @Size(max = 255) String email,
                               @Size(min = 8, max = 72) String password,
                               @NotNull StaffRole role,
                               Long branchId,
                               @Min(0) long compensationLimit,
                               StaffStatus status) {
    }

    public record StaffResponse(Long id, String fullName, String email, StaffRole role, Long branchId,
                                long compensationLimit, StaffStatus status, LocalDateTime createdAt) {

        public static StaffResponse from(Staff s) {
            return new StaffResponse(s.getId(), s.getFullName(), s.getEmail(), s.getRole(),
                    s.getBranch() == null ? null : s.getBranch().getId(), s.getCompensationLimit(), s.getStatus(), s.getCreatedAt());
        }
    }

    /** {@code notifyBeforeMinutes}: 10, 15 hoặc 30; bỏ trống = giữ nguyên (mặc định 15). */
    public record CustomerRequest(@NotBlank @Size(max = 255) String fullName,
                                  @NotBlank String phone,
                                  @Email @Size(max = 255) String email,
                                  Integer notifyBeforeMinutes,
                                  NotificationChannel preferredChannel) {
    }

    /** Không trả mật khẩu; {@code hasAccount} = khách đã đăng ký tài khoản. */
    public record CustomerResponse(Long id, String fullName, String phone, String email, boolean hasAccount,
                                   boolean phoneVerified, int notifyBeforeMinutes, NotificationChannel preferredChannel,
                                   LocalDateTime createdAt) {

        public static CustomerResponse from(Customer c) {
            return new CustomerResponse(c.getId(), c.getContact().fullName(), c.getContact().phone(), c.getContact().email(),
                    c.getPasswordHash() != null, c.getPhoneVerifiedAt() != null, c.getNotifyBeforeMinutes(),
                    c.getPreferredChannel(), c.getCreatedAt());
        }
    }
}
