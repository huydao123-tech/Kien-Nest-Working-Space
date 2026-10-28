package com.cowork.booking.domain;

import java.time.Duration;

/**
 * Tham số nghiệp vụ cấu hình được của đơn đặt chỗ.
 *
 * @param holdDuration       BR-02: thời gian giữ chỗ tạm khi thanh toán
 * @param checkInEarlyWindow BR-17: được check-in sớm tối đa bao lâu trước giờ bắt đầu
 * @param overstayGrace      BR-06: thời gian ân hạn trước khi tính phụ phí quá giờ
 */
public record BookingRules(Duration holdDuration, Duration checkInEarlyWindow, Duration overstayGrace) {
}
