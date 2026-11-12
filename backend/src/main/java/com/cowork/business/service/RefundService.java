package com.cowork.business.service;

import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.Payment;
import com.cowork.dataaccess.entity.Refund;
import com.cowork.dataaccess.enums.PaymentStatus;
import com.cowork.dataaccess.enums.RefundStatus;
import com.cowork.dataaccess.repository.PaymentRepository;
import com.cowork.dataaccess.repository.RefundRepository;
import com.cowork.dataaccess.valueobject.Money;
import com.cowork.dto.RefundDtos.RefundResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * UC06, UC25: ghi yêu cầu hoàn tiền ({@code refund}, trạng thái REQUESTED) vào giao dịch đã thanh toán thành công của đơn;
 * nhân viên xác nhận đã hoàn xong thì báo cho khách (FR-NOTI-04, ticket notification.md).
 * Việc gọi API hoàn tiền của cổng thanh toán chưa nằm trong phạm vi: nhân viên hoàn tay rồi đánh dấu xong.
 */
@Service
@Transactional
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final NotificationService notifications;
    private final Clock clock;

    public RefundService(PaymentRepository payments, RefundRepository refunds, NotificationService notifications, Clock clock) {
        this.payments = payments;
        this.refunds = refunds;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** Ghi yêu cầu hoàn {@code amount} cho đơn; số tiền 0 thì bỏ qua. Trả về rỗng nếu không ghi gì. */
    public Optional<Refund> request(Booking booking, Money amount, String reason, LocalDateTime now) {
        if (!amount.isPositive()) {
            return Optional.empty();
        }
        Optional<Payment> paid = payments.findByBookingId(booking.getId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCEEDED)
                .max(Comparator.comparing(Payment::getId));
        if (paid.isEmpty()) {
            // Đơn nhận tiền ngoài cổng (thu tại quầy) — chưa có nơi ghi hoàn tiền mặt, nhân viên xử lý tay.
            log.warn("Đơn {} cần hoàn {} VND nhưng không có giao dịch cổng thành công để ghi refund", booking.getCode(), amount.amount());
            return Optional.empty();
        }
        Payment payment = paid.get();
        Refund refund = new Refund(payment, Math.min(amount.amount(), payment.getAmount()), reason);
        refund.setRequestedAt(now);
        return Optional.of(refunds.save(refund));
    }

    /** Danh sách yêu cầu hoàn theo trạng thái, cũ nhất trước — nhân viên xem các khoản đang chờ hoàn (REQUESTED). */
    @Transactional(readOnly = true)
    public List<RefundResponse> list(RefundStatus status) {
        return refunds.findWithBookingByStatus(status).stream().map(RefundResponse::from).toList();
    }

    /**
     * FR-NOTI-04: nhân viên xác nhận đã hoàn tiền cho khách: REQUESTED → REFUNDED, ghi thời điểm xong, nhắn tin cho khách.
     * Khóa dòng refund trước khi kiểm tra trạng thái nên bấm hai lần (hoặc hai người cùng bấm) chỉ gửi một tin.
     */
    public RefundResponse complete(Long refundId) {
        Refund refund = refunds.findByIdForUpdate(refundId).orElseThrow(() -> new NotFoundException("Yêu cầu hoàn tiền", refundId));
        if (refund.getStatus() != RefundStatus.REQUESTED) {
            throw new DomainException("INVALID_REFUND_STATE",
                    "Chỉ xác nhận được yêu cầu đang chờ hoàn (REQUESTED), yêu cầu này đang " + refund.getStatus());
        }
        refund.setStatus(RefundStatus.REFUNDED);
        refund.setCompletedAt(LocalDateTime.now(clock));
        Refund saved = refunds.save(refund);
        notifications.refundCompleted(saved.getPayment().getBooking(), Money.vnd(saved.getAmount()));
        return RefundResponse.from(saved);
    }
}
