package com.cowork.notification.application;

import com.cowork.booking.domain.ContactInfo;
import com.cowork.booking.domain.event.BookingEvents.BookingCancelled;
import com.cowork.booking.domain.event.BookingEvents.BookingConfirmed;
import com.cowork.booking.domain.event.BookingEvents.BookingRefundRequired;
import com.cowork.booking.domain.event.BookingEvents.BookingReleased;
import com.cowork.booking.domain.event.BookingEvents.ReleaseReason;
import com.cowork.notification.application.NotificationSender.Recipient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;

/**
 * UC26 (phần thông báo theo sự kiện). Chỉ gửi sau khi thay đổi của đơn đã commit.
 * Lịch nhắc trước 30 phút / cảnh báo trước 15 phút chưa nằm trong phạm vi bản này.
 */
@Component
class BookingNotificationListener {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final NotificationSender sender;

    BookingNotificationListener(NotificationSender sender) {
        this.sender = sender;
    }

    @TransactionalEventListener
    void on(BookingConfirmed event) {
        sender.send(recipient(event.contact()), "Đặt chỗ thành công. Mã đơn " + event.code()
                + ", từ " + TIME.format(event.period().startAt()) + " đến " + TIME.format(event.period().endAt())
                + ". Xuất trình mã QR khi check-in.");
    }

    @TransactionalEventListener
    void on(BookingReleased event) {
        String reason = event.reason() == ReleaseReason.HOLD_EXPIRED
                ? "đã hết thời gian giữ chỗ" : "thanh toán không thành công";
        sender.send(recipient(event.contact()), "Đơn đặt chỗ của bạn " + reason + ". Vị trí đã được giải phóng.");
    }

    @TransactionalEventListener
    void on(BookingCancelled event) {
        sender.send(recipient(event.contact()), "Đơn đặt chỗ đã được hủy. Số tiền hoàn: " + event.refund().amount() + " VND.");
    }

    @TransactionalEventListener
    void on(BookingRefundRequired event) {
        if ("SLOT_TAKEN_AFTER_LATE_PAYMENT".equals(event.reason())) {
            sender.send(recipient(event.contact()), "Chúng tôi đã nhận thanh toán nhưng vị trí đã được đặt trước. "
                    + event.amount().amount() + " VND sẽ được hoàn lại cho bạn.");
        }
    }

    private static Recipient recipient(ContactInfo contact) {
        return new Recipient(contact.fullName(), contact.phone(), contact.email());
    }
}
