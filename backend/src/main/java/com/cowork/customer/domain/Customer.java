package com.cowork.customer.domain;

import com.cowork.shared.domain.AggregateRoot;
import com.cowork.shared.domain.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Khách hàng, định danh bằng số điện thoại. Khách vãng lai được tạo tự động ở lần đặt chỗ đầu tiên;
 * khi có chức năng đăng ký (UC01) thì cùng bản ghi này trở thành Member.
 */
@Entity
@Table(name = "customer")
public class Customer extends AggregateRoot {

    @Id
    private UUID id;

    @Embedded
    private ContactInfo contact;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;

    protected Customer() {
    }

    public static Customer register(ContactInfo contact, LocalDateTime now) {
        Customer customer = new Customer();
        customer.id = UUID.randomUUID();
        customer.contact = contact;
        customer.createdAt = now;
        return customer;
    }

    /** Cập nhật họ tên / email theo lần đặt mới nhất. Không xóa email đã có nếu lần này khách bỏ trống. */
    public void updateContact(ContactInfo latest) {
        if (!latest.phone().equals(contact.phone())) {
            throw new DomainException("PHONE_MISMATCH", "Không thể đổi số điện thoại định danh của khách");
        }
        String email = latest.hasEmail() ? latest.email() : contact.email();
        contact = new ContactInfo(latest.fullName(), latest.phone(), email);
    }

    public UUID id() {
        return id;
    }

    public ContactInfo contact() {
        return contact;
    }
}
