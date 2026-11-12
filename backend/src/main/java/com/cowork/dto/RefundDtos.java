package com.cowork.dto;

import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.Refund;
import com.cowork.dataaccess.enums.RefundStatus;

import java.time.LocalDateTime;

/** Yêu cầu hoàn tiền cho nhân viên xử lý (UC06, UC25, FR-NOTI-04). */
public final class RefundDtos {

    private RefundDtos() {
    }

    public record RefundResponse(Long id, Long bookingId, String bookingCode, long amount, String reason, RefundStatus status,
                                 LocalDateTime requestedAt, LocalDateTime completedAt) {

        /** Đọc đơn qua giao dịch (LAZY): gọi trong transaction của service. */
        public static RefundResponse from(Refund r) {
            Booking booking = r.getPayment().getBooking();
            return new RefundResponse(r.getId(), booking.getId(), booking.getCode(), r.getAmount(), r.getReason(), r.getStatus(),
                    r.getRequestedAt(), r.getCompletedAt());
        }
    }
}
