package com.cowork.dataaccess.entity;

import com.cowork.dataaccess.enums.NotificationChannel;
import com.cowork.dataaccess.enums.NotificationJobStatus;
import com.cowork.dataaccess.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Lịch thông báo gửi cho khách hàng. Bảng: {@code notification_job}. */
@Entity
@Table(name = "notification_job")
public class NotificationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationChannel channel;

    @Column(name = "send_at", nullable = false)
    private LocalDateTime sendAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationJobStatus status = NotificationJobStatus.SCHEDULED;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    public NotificationJob() {
    }

    public NotificationJob(Customer customer, Booking booking, NotificationType type, NotificationChannel channel, LocalDateTime sendAt) {
        this.customer = customer;
        this.booking = booking;
        this.type = type;
        this.channel = channel;
        this.sendAt = sendAt;
        this.status = NotificationJobStatus.SCHEDULED;
        this.attempts = 0;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public void setChannel(NotificationChannel channel) {
        this.channel = channel;
    }

    public LocalDateTime getSendAt() {
        return sendAt;
    }

    public void setSendAt(LocalDateTime sendAt) {
        this.sendAt = sendAt;
    }

    public NotificationJobStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationJobStatus status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}

