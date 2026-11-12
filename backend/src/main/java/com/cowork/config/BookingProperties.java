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
 * @param minDuration        UC02: thời lượng đặt tối thiểu (SRS Q1: 1 giờ)
 * @param changeNotice       UC06: hủy đơn / đổi khung giờ phải báo trước ít nhất bấy nhiêu so với giờ bắt đầu
 *                           (ticket booking.md: 2 giờ, hoàn 100%)
 * @param noShowGrace        BR-16: chờ khách check-in tối đa bấy nhiêu sau giờ bắt đầu, quá thì đơn thành NO_SHOW
 * @param noShowGracePercent BR-16: thời gian chờ không vượt quá bấy nhiêu phần trăm thời lượng đơn
 *                           (lấy giá trị nhỏ hơn giữa mục này và {@code noShowGrace})
 */
@ConfigurationProperties(prefix = "booking")
public record BookingProperties(
        @DefaultValue("10m") Duration holdDuration,
        @DefaultValue("15m") Duration checkInEarlyWindow,
        @DefaultValue("5m") Duration overstayGrace,
        @DefaultValue("1h") Duration minDuration,
        @DefaultValue("2h") Duration changeNotice,
        @DefaultValue("30m") Duration noShowGrace,
        @DefaultValue("50") int noShowGracePercent) {
}
