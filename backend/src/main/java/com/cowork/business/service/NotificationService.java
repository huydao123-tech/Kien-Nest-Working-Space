package com.cowork.business.service;

import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.ContactInfo;
import com.cowork.dataaccess.entity.Money;
import com.cowork.dataaccess.integration.NotificationSender;
import com.cowork.dataaccess.integration.NotificationSender.Recipient;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * UC26 (phần thông báo khi đơn đổi trạng thái). {@link BookingService} gọi trực tiếp sau khi lưu đơn.
 *
 * <p>Lưu ý: tin được gửi trong cùng giao dịch, trước khi commit. Hiện chỉ ghi log nên không ảnh hưởng;
 * khi nối SMS / Zalo thật nên chuyển việc gửi ra sau commit (hoặc qua bảng {@code notification_job}).
 * Lịch nhắc trước 30 phút / cảnh báo trước 15 phút chưa nằm trong phạm vi bản này.
 */
@Service
public class NotificationService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final NotificationSender sender;
    private final CustomerService customers;

    public NotificationService(NotificationSender sender, CustomerService customers) {
        this.sender = sender;
        this.customers = customers;
    }

    public void bookingConfirmed(Booking booking) {
        send(booking.getCustomerId(), "Đặt chỗ thành công. Mã đơn " + booking.getCode()
                + ", từ " + TIME.format(booking.getPeriod().startAt()) + " đến " + TIME.format(booking.getPeriod().endAt())
                + ". Xuất trình mã QR khi check-in.");
    }

    public void holdExpired(Booking booking) {
        send(booking.getCustomerId(), "Đơn đặt chỗ của bạn đã hết thời gian giữ chỗ. Vị trí đã được giải phóng.");
    }

    public void paymentFailed(Booking booking) {
        send(booking.getCustomerId(), "Đơn đặt chỗ của bạn thanh toán không thành công. Vị trí đã được giải phóng.");
    }

    public void bookingCancelled(Booking booking, Money refund) {
        send(booking.getCustomerId(), "Đơn đặt chỗ đã được hủy. Số tiền hoàn: " + refund.amount() + " VND.");
    }

    /** Khách đã trả tiền nhưng vị trí bị người khác đặt trước khi tiền về (BR-11). */
    public void refundAfterLatePayment(Booking booking, Money amount) {
        send(booking.getCustomerId(), "Chúng tôi đã nhận thanh toán nhưng vị trí đã được đặt trước. "
                + amount.amount() + " VND sẽ được hoàn lại cho bạn.");
    }

    private void send(UUID customerId, String message) {
        ContactInfo contact = customers.contactOf(customerId);
        sender.send(new Recipient(contact.fullName(), contact.phone(), contact.email()), message);
    }
}
