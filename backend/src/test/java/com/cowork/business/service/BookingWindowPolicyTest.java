package com.cowork.business.service;

import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.DomainException;
import com.cowork.config.BookingProperties;
import com.cowork.dataaccess.entity.Branch;
import com.cowork.dataaccess.enums.BranchStatus;
import com.cowork.dataaccess.valueobject.TimeRange;
import com.cowork.support.MutableClock;
import com.cowork.support.TestCaseLogRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/**
 * UC02, UC03: khung giờ đặt được không — hợp lệ, không ở quá khứ (cho trễ 5 phút), đủ 1 giờ, trong giờ mở cửa 07:00–22:00.
 * Viết theo {@code .claude/rules/unit-test.md}.
 */
public class BookingWindowPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 9, 0);

    @Rule
    public final TestCaseLogRule log = new TestCaseLogRule();

    private BookingWindowPolicy policy;
    private Branch branch;

    @Before
    public void setUp() {
        MutableClock clock = new MutableClock(ZoneId.of("Asia/Ho_Chi_Minh"));
        clock.set(NOW);
        BookingProperties properties = new BookingProperties(Duration.ofMinutes(10), Duration.ofMinutes(15),
                Duration.ofMinutes(5), Duration.ofHours(1), Duration.ofHours(2), Duration.ofMinutes(30), 50);
        policy = new BookingWindowPolicy(clock, properties);
        branch = new Branch("HCM-Q1", "Cowork Nguyễn Huệ", "72 Nguyễn Huệ", LocalTime.of(7, 0), LocalTime.of(22, 0));
    }

    // Viewpoint: VP3, VP7
    @Test
    public void test_validate_ValidWindow_001() {
        // Khung hợp lệ trong giờ mở cửa: trả về đúng khoảng thời gian.
        LocalDateTime start = NOW.plusHours(1);
        LocalDateTime end = NOW.plusHours(3);
        log.input("startAt = {}, endAt = {}, branch = {}", start, end, branch.getCode());

        TimeRange range = policy.validate(start, end, branch);

        log.output("range = {}", range);
        assertEquals(new TimeRange(start, end), range);
    }

    // Viewpoint: VP3
    @Test
    public void test_validate_EndEqualsStart_002() {
        // Giờ kết thúc bằng giờ bắt đầu.
        LocalDateTime start = NOW.plusHours(1);
        log.input("startAt = {}, endAt = {}, branch = {}", start, start, branch.getCode());

        DomainException thrown = assertThrows(DomainException.class, () -> policy.validate(start, start, branch));

        log.output("code = {}", thrown.code());
        assertEquals("INVALID_TIME_RANGE", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_validate_StartFiveMinutesAgo_003() {
        // Cận: bắt đầu đúng 5 phút trước vẫn đặt được.
        LocalDateTime start = NOW.minusMinutes(5);
        LocalDateTime end = NOW.plusHours(1);
        log.input("startAt = {}, endAt = {}, branch = {}", start, end, branch.getCode());

        TimeRange range = policy.validate(start, end, branch);

        log.output("startAt = {}", range.startAt());
        assertEquals(start, range.startAt());
    }

    // Viewpoint: VP3
    @Test
    public void test_validate_StartFiveMinutesOneSecondAgo_004() {
        // Quá 5 phút 1 giây: khung đã qua.
        LocalDateTime start = NOW.minusMinutes(5).minusSeconds(1);
        LocalDateTime end = NOW.plusHours(1);
        log.input("startAt = {}, endAt = {}, branch = {}", start, end, branch.getCode());

        DomainException thrown = assertThrows(DomainException.class, () -> policy.validate(start, end, branch));

        log.output("code = {}", thrown.code());
        assertEquals("START_IN_PAST", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_validate_EndAtNow_005() {
        // Giờ kết thúc đúng bằng hiện tại: khung đã qua.
        LocalDateTime start = NOW.minusHours(1);
        log.input("startAt = {}, endAt = {}, branch = {}", start, NOW, branch.getCode());

        DomainException thrown = assertThrows(DomainException.class, () -> policy.validate(start, NOW, branch));

        log.output("code = {}", thrown.code());
        assertEquals("START_IN_PAST", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_validate_DurationOneSecondShort_006() {
        // Thiếu 1 giây so với thời lượng tối thiểu 1 giờ.
        LocalDateTime start = NOW.plusHours(1);
        LocalDateTime end = start.plusHours(1).minusSeconds(1);
        log.input("startAt = {}, endAt = {}, branch = {}", start, end, branch.getCode());

        DomainException thrown = assertThrows(DomainException.class, () -> policy.validate(start, end, branch));

        log.output("code = {}", thrown.code());
        assertEquals("DURATION_TOO_SHORT", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_validate_DurationExactlyMinimum_007() {
        // Cận dưới: đúng 1 giờ.
        LocalDateTime start = NOW.plusHours(1);
        LocalDateTime end = start.plusHours(1);
        log.input("startAt = {}, endAt = {}, branch = {}", start, end, branch.getCode());

        TimeRange range = policy.validate(start, end, branch);

        log.output("duration = {}", range.duration());
        assertEquals(Duration.ofHours(1), range.duration());
    }

    // Viewpoint: VP7
    @Test
    public void test_validate_NullBranchSkipsOpeningHours_008() {
        // Vị trí chưa gán chi nhánh: bỏ qua kiểm tra giờ mở cửa (23:00 vẫn hợp lệ).
        LocalDateTime start = NOW.withHour(22);
        LocalDateTime end = NOW.withHour(23);
        log.input("startAt = {}, endAt = {}, branch = {}", start, end, null);

        TimeRange range = policy.validate(start, end, null);

        log.output("range = {}", range);
        assertEquals(new TimeRange(start, end), range);
    }

    // Viewpoint: VP3
    @Test
    public void test_validate_BranchClosed_009() {
        // Chi nhánh tạm ngưng.
        branch.setStatus(BranchStatus.CLOSED);
        LocalDateTime start = NOW.plusHours(1);
        LocalDateTime end = NOW.plusHours(2);
        log.input("startAt = {}, endAt = {}, branch = {}", start, end, branch.getCode());

        ConflictException thrown = assertThrows(ConflictException.class, () -> policy.validate(start, end, branch));

        log.output("code = {}", thrown.code());
        assertEquals("BRANCH_CLOSED", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_validate_ExactlyOpeningHours_010() {
        // Cận: 07:00–22:00 đúng giờ mở / đóng cửa (ngày hôm sau).
        LocalDateTime start = NOW.plusDays(1).withHour(7);
        LocalDateTime end = NOW.plusDays(1).withHour(22);
        log.input("startAt = {}, endAt = {}, branch = {}", start, end, branch.getCode());

        TimeRange range = policy.validate(start, end, branch);

        log.output("range = {}", range);
        assertEquals(new TimeRange(start, end), range);
    }

    // Viewpoint: VP3
    @Test
    public void test_validate_OneMinuteBeforeOpening_011() {
        // Bắt đầu 06:59.
        LocalDateTime start = NOW.plusDays(1).withHour(6).withMinute(59);
        LocalDateTime end = NOW.plusDays(1).withHour(9);
        log.input("startAt = {}, endAt = {}, branch = {}", start, end, branch.getCode());

        DomainException thrown = assertThrows(DomainException.class, () -> policy.validate(start, end, branch));

        log.output("code = {}", thrown.code());
        assertEquals("OUTSIDE_OPENING_HOURS", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_validate_OneMinuteAfterClosing_012() {
        // Kết thúc 22:01.
        LocalDateTime start = NOW.withHour(20);
        LocalDateTime end = NOW.withHour(22).withMinute(1);
        log.input("startAt = {}, endAt = {}, branch = {}", start, end, branch.getCode());

        DomainException thrown = assertThrows(DomainException.class, () -> policy.validate(start, end, branch));

        log.output("code = {}", thrown.code());
        assertEquals("OUTSIDE_OPENING_HOURS", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_validate_SpansTwoDays_013() {
        // Vắt qua hai ngày.
        LocalDateTime start = NOW.withHour(21);
        LocalDateTime end = NOW.plusDays(1).withHour(8);
        log.input("startAt = {}, endAt = {}, branch = {}", start, end, branch.getCode());

        DomainException thrown = assertThrows(DomainException.class, () -> policy.validate(start, end, branch));

        log.output("code = {}", thrown.code());
        assertEquals("OUTSIDE_OPENING_HOURS", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_validate_NullStart_014() {
        // Giờ bắt đầu null.
        LocalDateTime end = NOW.plusHours(2);
        log.input("startAt = {}, endAt = {}, branch = {}", null, end, branch.getCode());

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> policy.validate(null, end, branch));

        log.output("exception = {}", thrown.getClass().getSimpleName());
        assertEquals(NullPointerException.class, thrown.getClass());
    }
}
