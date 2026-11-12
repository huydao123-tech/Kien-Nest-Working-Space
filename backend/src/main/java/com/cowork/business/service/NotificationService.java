package com.cowork.business.service;

import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.Customer;
import com.cowork.dataaccess.entity.NotificationJob;
import com.cowork.dataaccess.entity.SpaceResource;
import com.cowork.dataaccess.entity.Staff;
import com.cowork.dataaccess.enums.NotificationJobStatus;
import com.cowork.dataaccess.enums.NotificationType;
import com.cowork.dataaccess.enums.StaffRole;
import com.cowork.dataaccess.enums.StaffStatus;
import com.cowork.dataaccess.integration.NotificationSender;
import com.cowork.dataaccess.integration.NotificationSender.Recipient;
import com.cowork.dataaccess.repository.NotificationJobRepository;
import com.cowork.dataaccess.repository.StaffRepository;
import com.cowork.dataaccess.valueobject.ContactInfo;
import com.cowork.dataaccess.valueobject.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * UC26: thông báo cho khách khi đơn đổi trạng thái, cảnh báo sắp hết giờ (FR-NOTI-03, FR-NOTI-04); cảnh báo quá giờ
 * cho quầy (FR-SYS-02). Các service khác gọi trực tiếp sau khi lưu.
 *
 * <p>Lưu ý: tin được gửi trong cùng giao dịch, trước khi commit. Hiện chỉ ghi log nên không ảnh hưởng;
 * khi nối SMS / Zalo thật nên chuyển việc gửi ra sau commit (hoặc qua bảng {@code notification_job}).
 * Nhắc lịch trước 30 phút giờ bắt đầu (BR-05) chưa nằm trong phạm vi bản này.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final NotificationSender sender;
    private final NotificationJobRepository jobs;
    private final StaffRepository staff;
    private final Clock clock;

    public NotificationService(NotificationSender sender, NotificationJobRepository jobs, StaffRepository staff, Clock clock) {
        this.sender = sender;
        this.jobs = jobs;
        this.staff = staff;
        this.clock = clock;
    }

    public void bookingConfirmed(Booking booking) {
        send(booking, "Đặt chỗ thành công. Mã đơn " + booking.getCode()
                + ", từ " + TIME.format(booking.getPeriod().startAt()) + " đến " + TIME.format(booking.getPeriod().endAt())
                + ". Xuất trình mã QR khi check-in.");
    }

    public void holdExpired(Booking booking) {
        send(booking, "Đơn đặt chỗ của bạn đã hết thời gian giữ chỗ. Vị trí đã được giải phóng.");
    }

    public void paymentFailed(Booking booking) {
        send(booking, "Đơn đặt chỗ của bạn thanh toán không thành công. Vị trí đã được giải phóng.");
    }

    public void bookingCancelled(Booking booking, Money refund) {
        send(booking, "Đơn đặt chỗ đã được hủy. Số tiền hoàn: " + refund.amount() + " VND.");
    }

    /** Đổi khung giờ thành công; {@code refund} > 0 khi khung mới rẻ hơn số tiền đã trả. */
    public void bookingRescheduled(Booking booking, Money refund) {
        String message = "Đơn " + booking.getCode() + " đã đổi sang " + TIME.format(booking.getPeriod().startAt())
                + " – " + TIME.format(booking.getPeriod().endAt()) + ".";
        if (refund.isPositive()) {
            message += " Số tiền chênh lệch được hoàn: " + refund.amount() + " VND.";
        }
        send(booking, message);
    }

    /** Yêu cầu đổi chỗ không được thanh toán phần chênh (lỗi / quá hạn): đơn giữ nguyên chỗ cũ. */
    public void rescheduleCancelled(Booking booking) {
        send(booking, "Yêu cầu đổi chỗ của đơn " + booking.getCode() + " chưa được thanh toán nên đã hủy. Đơn giữ nguyên "
                + TIME.format(booking.getPeriod().startAt()) + " – " + TIME.format(booking.getPeriod().endAt()) + ".");
    }

    /** UC01: gửi mã OTP xác thực số điện thoại. */
    public void otp(String phone, String code) {
        sender.send(new Recipient(null, phone, null), "Ma xac thuc Cowork cua ban la " + code + ". Khong chia se ma nay cho bat ky ai.");
    }

    /** Khách đã trả tiền nhưng vị trí bị người khác đặt trước khi tiền về (BR-11). */
    public void refundAfterLatePayment(Booking booking, Money amount) {
        send(booking, "Chúng tôi đã nhận thanh toán nhưng vị trí đã được đặt trước. "
                + amount.amount() + " VND sẽ được hoàn lại cho bạn.");
    }

    /** FR-NOTI-04: nhân viên xác nhận đã hoàn tiền xong cho khách. */
    public void refundCompleted(Booking booking, Money amount) {
        send(booking, "Đã hoàn " + amount.amount() + " VND cho đơn " + booking.getCode()
                + ". Tiền về tài khoản tùy thời gian xử lý của ngân hàng.");
    }

    /** UC24, BR-16: khách không check-in trong thời gian chờ, đơn chuyển NO_SHOW, không hoàn tiền. */
    public void noShow(Booking booking) {
        send(booking, "Đơn " + booking.getCode() + " (" + TIME.format(booking.getPeriod().startAt()) + " – "
                + TIME.format(booking.getPeriod().endAt()) + ") đã bị hủy do bạn không check-in đúng hạn. "
                + "Theo chính sách, đơn không được hoàn tiền.");
    }

    /** FR-OPS-07, FR-NOTI-04: vị trí của đơn chuyển sang bảo trì; quầy sẽ liên hệ sắp xếp lại. */
    public void resourceMaintenance(Booking booking) {
        send(booking, "Vị trí " + booking.getResource().getCode() + " của đơn " + booking.getCode() + " ("
                + TIME.format(booking.getPeriod().startAt()) + " – " + TIME.format(booking.getPeriod().endAt())
                + ") đang bảo trì. Nhân viên sẽ liên hệ để đổi sang chỗ tương đương hoặc hoàn tiền.");
    }

    /**
     * FR-NOTI-03, BR-05: cảnh báo sắp hết giờ, gửi khi còn {@code customer.notifyBeforeMinutes} phút (10 / 15 / 30,
     * mặc định 15) trước giờ kết thúc, kèm lời nhắc gia hạn nhanh. Mốc gửi là [giờ kết thúc − số phút, giờ kết thúc).
     * Mỗi mốc chỉ gửi một lần, ghi dòng {@code notification_job} (END_WARNING, SENT) để chống gửi trùng; gia hạn làm đổi
     * giờ kết thúc nên có mốc mới và được cảnh báo lại (FR-NOTI-05). Trả về {@code true} nếu đã gửi.
     */
    public boolean endWarning(Booking booking) {
        LocalDateTime now = LocalDateTime.now(clock);
        Customer customer = booking.getCustomer();
        LocalDateTime endAt = booking.getPeriod().endAt();
        LocalDateTime sendAt = endAt.minusMinutes(customer.getNotifyBeforeMinutes());
        if (now.isBefore(sendAt) || !now.isBefore(endAt)) {
            return false;
        }
        if (jobs.existsByBookingIdAndTypeAndSendAt(booking.getId(), NotificationType.END_WARNING, sendAt)) {
            return false;
        }
        NotificationJob job = new NotificationJob(customer, booking, NotificationType.END_WARNING,
                customer.getPreferredChannel(), sendAt);
        job.setStatus(NotificationJobStatus.SENT);
        job.setAttempts(1);
        job.setSentAt(now);
        jobs.save(job);
        send(booking, "Đơn " + booking.getCode() + " tại " + booking.getResource().getCode() + " sẽ hết giờ lúc "
                + TIME.format(endAt) + ". Mở ứng dụng, chọn \"Gia hạn\" để ngồi thêm nếu vị trí còn trống.");
        return true;
    }

    /**
     * FR-SYS-02: báo cho lễ tân đang hoạt động của chi nhánh chứa vị trí khi khách ngồi quá giờ.
     * Trả về số nhân viên đã nhận; chi nhánh không có lễ tân nào thì chỉ ghi log cảnh báo.
     */
    public int overstay(Booking booking) {
        SpaceResource resource = booking.getResource();
        List<Staff> receptionists = resource.getZone() == null ? List.of()
                : staff.findByBranchIdAndRoleAndStatusOrderByIdAsc(resource.getZone().getBranch().getId(),
                StaffRole.RECEPTIONIST, StaffStatus.ACTIVE);
        if (receptionists.isEmpty()) {
            log.warn("Đơn {} quá giờ nhưng chi nhánh của vị trí {} không có lễ tân nào để báo", booking.getCode(), resource.getCode());
            return 0;
        }
        String message = "Quá giờ: vị trí " + resource.getCode() + ", đơn " + booking.getCode() + " ("
                + booking.getCustomer().getContact().fullName() + ") đã hết giờ lúc " + TIME.format(booking.getPeriod().endAt())
                + " nhưng chưa check-out.";
        for (Staff member : receptionists) {
            sender.send(new Recipient(member.getFullName(), null, member.getEmail()), message);
        }
        return receptionists.size();
    }

    /** Gửi cho khách của đơn; gọi trong giao dịch của service gọi tới nên đọc được khách (LAZY) qua quan hệ. */
    private void send(Booking booking, String message) {
        ContactInfo contact = booking.getCustomer().getContact();
        sender.send(new Recipient(contact.fullName(), contact.phone(), contact.email()), message);
    }
}
