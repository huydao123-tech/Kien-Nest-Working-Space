package com.cowork.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Tham số đăng nhập / phân quyền (UC01, UC23), đọc từ mục {@code auth:} trong application.yml.
 *
 * @param jwtSecret          khóa ký JWT (HS256), tối thiểu 32 byte. Production phải đặt qua biến môi trường JWT_SECRET
 * @param jwtIssuer          giá trị claim {@code iss}, token khác issuer bị từ chối
 * @param accessTokenTtl     thời hạn access token
 * @param otpTtl             thời hạn mã OTP
 * @param otpMaxAttempts     số lần nhập sai tối đa của một mã OTP
 * @param otpResendCooldown  khoảng cách tối thiểu giữa hai lần gửi OTP cho cùng một số
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        String jwtSecret,
        @DefaultValue("cowork-booking") String jwtIssuer,
        @DefaultValue("8h") Duration accessTokenTtl,
        @DefaultValue("5m") Duration otpTtl,
        @DefaultValue("5") int otpMaxAttempts,
        @DefaultValue("60s") Duration otpResendCooldown) {
}
