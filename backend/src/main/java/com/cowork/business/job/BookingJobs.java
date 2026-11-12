package com.cowork.business.job;

import com.cowork.business.exception.DomainException;
import com.cowork.business.service.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/** UC24: các chuyển trạng thái tự động theo thời gian. */
@Component
@ConditionalOnProperty(name = "booking.jobs.enabled", havingValue = "true", matchIfMissing = true)
class BookingJobs {

    private static final Logger log = LoggerFactory.getLogger(BookingJobs.class);

    private final BookingService bookingService;

    BookingJobs(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** FR-SYS-01: giải phóng vị trí của các đơn giữ chỗ quá hạn. */
    @Scheduled(fixedDelayString = "${booking.jobs.hold-expiry-delay-ms:30000}")
    void expireHolds() {
        bookingService.findExpiredHoldIds().forEach(id -> runSafely(id, bookingService::expireHold));
    }

    /** booking.md Prompt 2: hủy các yêu cầu đổi chỗ quá hạn thanh toán phần chênh, nhả chỗ mới. */
    @Scheduled(fixedDelayString = "${booking.jobs.hold-expiry-delay-ms:30000}")
    void expirePendingChanges() {
        bookingService.findExpiredPendingChangeIds().forEach(id -> runSafely(id, bookingService::expirePendingChange));
    }

    /** FR-SYS-02: đánh dấu khách ngồi quá giờ để quầy xử lý. */
    @Scheduled(fixedDelayString = "${booking.jobs.overstay-delay-ms:60000}")
    void flagOverstays() {
        bookingService.findOverdueIds().forEach(id -> runSafely(id, bookingService::flagOverstay));
    }

    /** FR-SYS-03, BR-16: đánh dấu khách không đến (no-show) và báo cho khách. */
    @Scheduled(fixedDelayString = "${booking.jobs.no-show-delay-ms:60000}")
    void markNoShows() {
        bookingService.findNoShowIds().forEach(id -> runSafely(id, bookingService::markNoShow));
    }

    /** FR-NOTI-03: cảnh báo sắp hết giờ cho khách đang ngồi. */
    @Scheduled(fixedDelayString = "${booking.jobs.end-warning-delay-ms:30000}")
    void warnEndingSoon() {
        bookingService.findEndingSoonIds().forEach(id -> runSafely(id, bookingService::warnEndingSoon));
    }

    private static void runSafely(Long bookingId, Consumer<Long> action) {
        try {
            action.accept(bookingId);
        } catch (OptimisticLockingFailureException | DomainException e) {
            // Đơn vừa được thao tác khác cập nhật (ví dụ webhook thanh toán) — lượt quét sau sẽ xét lại.
            log.debug("Bỏ qua đơn {}: {}", bookingId, e.getMessage());
        }
    }
}
