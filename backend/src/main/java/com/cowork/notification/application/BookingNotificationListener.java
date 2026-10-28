package com.cowork.notification.application;

import com.cowork.booking.domain.event.BookingEvents.BookingCancelled;
import com.cowork.booking.domain.event.BookingEvents.BookingConfirmed;
import com.cowork.booking.domain.event.BookingEvents.BookingRefundRequired;
import com.cowork.booking.domain.event.BookingEvents.BookingReleased;
import com.cowork.booking.domain.event.BookingEvents.ReleaseReason;
import com.cowork.customer.application.CustomerService;
import com.cowork.customer.domain.ContactInfo;
import com.cowork.notification.application.NotificationSender.Recipient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * UC26 (phần thông báo theo sự kiện). Chỉ gửi sau khi thay đổi của đơn đã commit.
 * Đọc thông tin liên hệ trong một giao dịch mới vì giao dịch phát event đã kết thúc.
 * Lịch nhắc trước 30 phút / cảnh báo trước 15 phút chưa nằm trong phạm vi bản này.
 */
@Component
class BookingNotificationListener {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final NotificationSender sender;
    private final CustomerService customers;

    BookingNotificationListener(NotificationSender sender, CustomerService customers) {
        this.sender = sender;
        this.customers = customers;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    void on(BookingConfirmed event) {
        send(event.customerId(), "Đặt chỗ thành công. Mã đơn " + event.code()
                + ", từ " + TIME.format(event.period().startAt()) + " đến " + TIME.format(event.period().endAt())
                + ". Xuất trình mã QR khi check-in.");
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    void on(BookingReleased event) {
        String reason = event.reason() == ReleaseReason.HOLD_EXPIRED
                ? "đã hết thời gian giữ chỗ" : "thanh toán không thành công";
        send(event.customerId(), "Đơn đặt chỗ của bạn " + reason + ". Vị trí đã được giải phóng.");
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    void on(BookingCancelled event) {
        send(event.customerId(), "Đơn đặt chỗ đã được hủy. Số tiền hoàn: " + event.refund().amount() + " VND.");
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    void on(BookingRefundRequired event) {
        if ("SLOT_TAKEN_AFTER_LATE_PAYMENT".equals(event.reason())) {
            send(event.customerId(), "Chúng tôi đã nhận thanh toán nhưng vị trí đã được đặt trước. "
                    + event.amount().amount() + " VND sẽ được hoàn lại cho bạn.");
        }
    }

    private void send(UUID customerId, String message) {
        ContactInfo contact = customers.contactOf(customerId);
        sender.send(new Recipient(contact.fullName(), contact.phone(), contact.email()), message);
    }
}
