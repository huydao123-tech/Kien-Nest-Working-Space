package com.cowork.customer.domain;

import com.cowork.shared.domain.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.regex.Pattern;

/** Thông tin liên hệ của khách (Member hoặc khách vãng lai). */
@Embeddable
public record ContactInfo(
        @Column(name = "full_name", nullable = false) String fullName,
        @Column(name = "phone", nullable = false, length = 15) String phone,
        @Column(name = "email") String email) {

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

    public boolean hasEmail() {
        return email != null && !email.isBlank();
    }
}
