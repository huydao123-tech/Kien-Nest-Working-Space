package com.cowork.business.service;

import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.DomainException;
import com.cowork.config.BookingProperties;
import com.cowork.dataaccess.entity.Branch;
import com.cowork.dataaccess.enums.BranchStatus;
import com.cowork.dataaccess.valueobject.TimeRange;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * UC02 bước 3: khung giờ khách chọn có đặt được không. Dùng chung cho tìm chỗ (UC02) và giữ chỗ (UC03),
 * để khách không lách được quy tắc bằng cách gọi thẳng API giữ chỗ.
 */
@Component
public class BookingWindowPolicy {

    /** Cho phép đặt khung vừa bắt đầu tối đa 5 phút trước (khách đến muộn một chút vẫn đặt được). */
    private static final Duration PAST_TOLERANCE = Duration.ofMinutes(5);

    private final Clock clock;
    private final BookingProperties properties;

    public BookingWindowPolicy(Clock clock, BookingProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    /**
     * Kiểm tra lần lượt: giờ kết thúc sau giờ bắt đầu → không ở quá khứ → đủ thời lượng tối thiểu
     * → chi nhánh đang hoạt động → nằm trọn trong giờ mở cửa của một ngày.
     * {@code branch} rỗng (vị trí chưa gán khu vực) thì bỏ qua hai bước kiểm tra chi nhánh.
     */
    public TimeRange validate(LocalDateTime startAt, LocalDateTime endAt, Branch branch) {
        if (!endAt.isAfter(startAt)) {
            throw new DomainException("INVALID_TIME_RANGE", "Giờ kết thúc phải sau giờ bắt đầu");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (!endAt.isAfter(now) || startAt.isBefore(now.minus(PAST_TOLERANCE))) {
            throw new DomainException("START_IN_PAST", "Không thể đặt khung giờ đã qua");
        }
        TimeRange range = new TimeRange(startAt, endAt);
        if (range.duration().compareTo(properties.minDuration()) < 0) {
            throw new DomainException("DURATION_TOO_SHORT",
                    "Thời lượng đặt tối thiểu là " + properties.minDuration().toMinutes() + " phút");
        }
        if (branch != null) {
            requireOpen(branch, range);
        }
        return range;
    }

    private static void requireOpen(Branch branch, TimeRange range) {
        if (branch.getStatus() != BranchStatus.ACTIVE) {
            throw new ConflictException("BRANCH_CLOSED", "Chi nhánh " + branch.getName() + " đang tạm ngưng hoạt động");
        }
        boolean sameDay = range.startAt().toLocalDate().equals(range.endAt().toLocalDate());
        boolean afterOpen = !range.startAt().toLocalTime().isBefore(branch.getOpenTime());
        boolean beforeClose = !range.endAt().toLocalTime().isAfter(branch.getCloseTime());
        if (!sameDay || !afterOpen || !beforeClose) {
            throw new DomainException("OUTSIDE_OPENING_HOURS",
                    "Chi nhánh " + branch.getName() + " mở cửa từ " + branch.getOpenTime() + " đến " + branch.getCloseTime());
        }
    }
}
