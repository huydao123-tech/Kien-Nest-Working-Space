package com.cowork.booking.domain;

import com.cowork.shared.domain.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.regex.Pattern;

/** Thông tin định danh của khách (Member hoặc khách vãng lai). */
@Embeddable
public record ContactInfo(
        @Column(name = "contact_name", nullable = false) String fullName,
        @Column(name = "contact_phone", nullable = false, length = 15) String phone,
        @Column(name = "contact_email") String email) {

    private static final Pattern VN_PHONE = Pattern.compile("^(0|\\+84)\\d{9}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public ContactInfo {
        if (fullName == null || fullName.isBlank()) {
            throw new DomainException("INVALID_CONTACT", "Họ tên là bắt buộc");
        }
        if (phone == null || !VN_PHONE.matcher(phone).matches()) {
            throw new DomainException("INVALID_CONTACT", "Số điện thoại không hợp lệ");
        }
        if (email != null && !email.isBlank() && !EMAIL.matcher(email).matches()) {
            throw new DomainException("INVALID_CONTACT", "Email không hợp lệ");
        }
    }
}
