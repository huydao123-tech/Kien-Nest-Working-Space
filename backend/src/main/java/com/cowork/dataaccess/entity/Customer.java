package com.cowork.dataaccess.entity;

import com.cowork.dataaccess.enums.NotificationChannel;
import com.cowork.dataaccess.valueobject.ContactInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/** Khách hàng, định danh bằng số điện thoại. */
@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private ContactInfo contact;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;

    @Column(name = "phone_verified_at")
    private LocalDateTime phoneVerifiedAt;

    @Column(name = "notify_before_minutes", nullable = false)
    private int notifyBeforeMinutes = 15;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_channel", nullable = false, length = 10)
    private NotificationChannel preferredChannel = NotificationChannel.SMS;

    /** BCrypt của mật khẩu (UC01). Rỗng = khách vãng lai, chưa đăng ký tài khoản. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    public Customer() {
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ContactInfo getContact() {
        return contact;
    }

    public void setContact(ContactInfo contact) {
        this.contact = contact;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getPhoneVerifiedAt() {
        return phoneVerifiedAt;
    }

    public void setPhoneVerifiedAt(LocalDateTime phoneVerifiedAt) {
        this.phoneVerifiedAt = phoneVerifiedAt;
    }

    public int getNotifyBeforeMinutes() {
        return notifyBeforeMinutes;
    }

    public void setNotifyBeforeMinutes(int notifyBeforeMinutes) {
        this.notifyBeforeMinutes = notifyBeforeMinutes;
    }

    public NotificationChannel getPreferredChannel() {
        return preferredChannel;
    }

    public void setPreferredChannel(NotificationChannel preferredChannel) {
        this.preferredChannel = preferredChannel;
    }
}
