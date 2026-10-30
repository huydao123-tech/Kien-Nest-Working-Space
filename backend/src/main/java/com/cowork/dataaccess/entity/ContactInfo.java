package com.cowork.dataaccess.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Thông tin liên hệ của khách. Kiểm tra hợp lệ nằm ở {@code CustomerService}. */
@Embeddable
public record ContactInfo(
        @Column(name = "full_name", nullable = false) String fullName,
        @Column(name = "phone", nullable = false, length = 15) String phone,
        @Column(name = "email") String email) {

    public boolean hasEmail() {
        return email != null && !email.isBlank();
    }
}
