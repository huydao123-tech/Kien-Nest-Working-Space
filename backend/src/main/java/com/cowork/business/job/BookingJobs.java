package com.cowork.business.job;

import com.cowork.business.exception.DomainException;
import com.cowork.business.service.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;
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

    /** FR-SYS-02: đánh dấu khách ngồi quá giờ để quầy xử lý. */
    @Scheduled(fixedDelayString = "${booking.jobs.overstay-delay-ms:60000}")
    void flagOverstays() {
        bookingService.findOverdueIds().forEach(id -> runSafely(id, bookingService::flagOverstay));
    }

    private static void runSafely(UUID bookingId, Consumer<UUID> action) {
        try {
            action.accept(bookingId);
        } catch (OptimisticLockingFailureException | DomainException e) {
            // Đơn vừa được thao tác khác cập nhật (ví dụ webhook thanh toán) — lượt quét sau sẽ xét lại.
            log.debug("Bỏ qua đơn {}: {}", bookingId, e.getMessage());
        }
    }
}
