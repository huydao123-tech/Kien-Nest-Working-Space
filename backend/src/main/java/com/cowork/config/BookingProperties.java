package com.cowork.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Tham số nghiệp vụ của đơn đặt chỗ, đọc từ mục {@code booking:} trong application.yml.
 *
 * @param holdDuration       BR-02: thời gian giữ chỗ tạm khi thanh toán
 * @param checkInEarlyWindow BR-17: được check-in sớm tối đa bao lâu trước giờ bắt đầu
 * @param overstayGrace      BR-06: thời gian ân hạn trước khi tính phụ phí quá giờ
 */
@ConfigurationProperties(prefix = "booking")
public record BookingProperties(
        @DefaultValue("10m") Duration holdDuration,
        @DefaultValue("15m") Duration checkInEarlyWindow,
        @DefaultValue("5m") Duration overstayGrace) {
}
