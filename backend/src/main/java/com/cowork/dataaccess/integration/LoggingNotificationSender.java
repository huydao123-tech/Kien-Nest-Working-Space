package com.cowork.dataaccess.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Ghi log thay cho gửi thật. Thay bằng adapter SMS/Zalo/Email khi tích hợp. */
@Component
class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void send(Recipient recipient, String message) {
        log.info("[notify] {} ({}): {}", recipient.fullName(), maskPhone(recipient.phone()), message);
    }

    /** NFR-09: không ghi đầy đủ số điện thoại vào log. */
    private static String maskPhone(String phone) {
        return phone.length() <= 4 ? "****" : "*".repeat(phone.length() - 3) + phone.substring(phone.length() - 3);
    }
}
