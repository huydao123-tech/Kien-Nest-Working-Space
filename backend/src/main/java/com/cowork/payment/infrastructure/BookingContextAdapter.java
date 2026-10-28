package com.cowork.payment.infrastructure;

import com.cowork.booking.application.BookingService;
import com.cowork.booking.domain.Booking;
import com.cowork.booking.domain.BookingStatus;
import com.cowork.payment.application.PayableBookingProvider;
import com.cowork.shared.domain.ConflictException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Dịch mô hình Booking sang mô hình Payment cần, để Payment không phụ thuộc trực tiếp vào aggregate Booking. */
@Component
class BookingContextAdapter implements PayableBookingProvider {

    private final BookingService bookingService;

    BookingContextAdapter(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Override
    public PayableBooking payableBooking(UUID bookingId) {
        Booking booking = bookingService.get(bookingId);
        if (booking.status() != BookingStatus.HELD) {
            throw new ConflictException("BOOKING_NOT_PAYABLE", "Đơn đang ở trạng thái " + booking.status());
        }
        return new PayableBooking(booking.id(), booking.code(), booking.outstanding(), booking.holdExpiresAt());
    }
}
