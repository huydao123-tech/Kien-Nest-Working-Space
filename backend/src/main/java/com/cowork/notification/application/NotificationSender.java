package com.cowork.notification.application;

/** Cổng ra tới dịch vụ SMS / Zalo ZNS / Email / Push. */
public interface NotificationSender {

    void send(Recipient recipient, String message);

    record Recipient(String fullName, String phone, String email) {
    }
}
