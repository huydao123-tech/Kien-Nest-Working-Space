package com.cowork.booking.application;

import com.cowork.payment.domain.event.PaymentEvents.PaymentFailed;
import com.cowork.payment.domain.event.PaymentEvents.PaymentSucceeded;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Booking lắng nghe event của Payment sau khi giao dịch thanh toán đã commit.
 * REQUIRES_NEW là bắt buộc: ở pha AFTER_COMMIT, giao dịch cũ đã kết thúc nên mọi thay đổi
 * phải nằm trong một giao dịch mới mới được ghi xuống.
 */
@Component
class PaymentEventsListener {

    private final BookingService bookingService;

    PaymentEventsListener(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void on(PaymentSucceeded event) {
        bookingService.onPaymentSucceeded(event.bookingId(), event.amount());
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void on(PaymentFailed event) {
        bookingService.onPaymentFailed(event.bookingId());
    }
}
