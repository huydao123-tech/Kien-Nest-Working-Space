package com.cowork.business.service;

import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.ResourceType;
import com.cowork.dataaccess.entity.SpaceResource;
import com.cowork.dataaccess.enums.BookingStatus;
import com.cowork.dataaccess.enums.ResourceStatus;
import com.cowork.dataaccess.enums.ResourceTypeCode;
import com.cowork.dataaccess.repository.BookingRepository;
import com.cowork.dataaccess.repository.SpaceResourceRepository;
import com.cowork.support.MutableClock;
import com.cowork.support.TestCaseLogRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BR-09, BR-14, UC17: vị trí đặt được không, đánh dấu bảo trì (kèm báo khách bị ảnh hưởng — FR-OPS-07) / mở lại.
 * Viết theo {@code .claude/rules/unit-test.md}.
 */
public class ResourceServiceTest {

    private static final Long ID = 10L;
    private static final int CAPACITY = 4;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 8, 0);

    @Rule
    public final TestCaseLogRule log = new TestCaseLogRule();

    private SpaceResourceRepository resources;
    private BookingRepository bookings;
    private NotificationService notifications;
    private ResourceService service;
    private SpaceResource resource;

    @Before
    public void setUp() {
        resources = mock(SpaceResourceRepository.class);
        bookings = mock(BookingRepository.class);
        notifications = mock(NotificationService.class);
        MutableClock clock = new MutableClock(ZoneId.of("Asia/Ho_Chi_Minh"));
        clock.set(NOW);
        service = new ResourceService(resources, bookings, notifications, clock);
        resource = new SpaceResource("MR-01", "Phòng họp", new ResourceType(ResourceTypeCode.MEETING_ROOM, "Phòng họp", null), CAPACITY);
        resource.setId(ID);
        when(resources.findById(ID)).thenReturn(Optional.of(resource));
        when(resources.save(any(SpaceResource.class))).thenAnswer(call -> call.getArgument(0));
    }

    // ---------- requireBookable ----------

    // Viewpoint: VP1, VP3
    @Test
    public void test_requireBookable_PartySizeEqualsCapacity_001() {
        // Cận trên: số người bằng sức chứa → hợp lệ, không ném lỗi.
        int partySize = CAPACITY;
        log.input("resource = {}, partySize = {}", resource.getCode(), partySize);

        service.requireBookable(resource, partySize);

        log.output("status = {}", resource.getStatus());
        assertEquals(ResourceStatus.ACTIVE, resource.getStatus());
    }

    // Viewpoint: VP1, VP3
    @Test
    public void test_requireBookable_PartySizeOverCapacity_002() {
        // Vượt sức chứa 1 người (BR-14).
        int partySize = CAPACITY + 1;
        log.input("resource = {}, partySize = {}", resource.getCode(), partySize);

        DomainException thrown = assertThrows(DomainException.class, () -> service.requireBookable(resource, partySize));

        log.output("code = {}", thrown.code());
        assertEquals("CAPACITY_EXCEEDED", thrown.code());
    }

    // Viewpoint: VP1
    @Test
    public void test_requireBookable_PartySizeMin_003() {
        // Cận dưới số người (Integer.MIN_VALUE): không vượt sức chứa.
        int partySize = Integer.MIN_VALUE;
        log.input("resource = {}, partySize = {}", resource.getCode(), partySize);

        service.requireBookable(resource, partySize);

        log.output("status = {}", resource.getStatus());
        assertEquals(ResourceStatus.ACTIVE, resource.getStatus());
    }

    // Viewpoint: VP1
    @Test
    public void test_requireBookable_PartySizeMax_004() {
        // Cận trên số người (Integer.MAX_VALUE).
        int partySize = Integer.MAX_VALUE;
        log.input("resource = {}, partySize = {}", resource.getCode(), partySize);

        DomainException thrown = assertThrows(DomainException.class, () -> service.requireBookable(resource, partySize));

        log.output("code = {}", thrown.code());
        assertEquals("CAPACITY_EXCEEDED", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_requireBookable_Maintenance_005() {
        // Vị trí đang bảo trì (BR-09).
        resource.setStatus(ResourceStatus.MAINTENANCE);
        log.input("resource = {}, partySize = {}", resource.getCode(), 1);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.requireBookable(resource, 1));

        log.output("code = {}", thrown.code());
        assertEquals("RESOURCE_NOT_BOOKABLE", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_requireBookable_Inactive_006() {
        // Vị trí đã ngưng khai thác.
        resource.setStatus(ResourceStatus.INACTIVE);
        log.input("resource = {}, partySize = {}", resource.getCode(), 1);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.requireBookable(resource, 1));

        log.output("code = {}", thrown.code());
        assertEquals("RESOURCE_NOT_BOOKABLE", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_requireBookable_NullResource_007() {
        // Vị trí null.
        SpaceResource nothing = null;
        log.input("resource = {}, partySize = {}", nothing, 1);

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.requireBookable(nothing, 1));

        log.output("exception = {}", thrown.getClass().getSimpleName());
        assertEquals(NullPointerException.class, thrown.getClass());
    }

    // ---------- markMaintenance ----------

    // Viewpoint: VP3
    @Test
    public void test_markMaintenance_Active_008() {
        // Vị trí đang khai thác → bảo trì.
        log.input("id = {}", ID);

        SpaceResource result = service.markMaintenance(ID);

        log.output("status = {}", result.getStatus());
        assertEquals(ResourceStatus.MAINTENANCE, result.getStatus());
        verify(resources).save(resource);
    }

    // Viewpoint: VP3
    @Test
    public void test_markMaintenance_Inactive_009() {
        // Vị trí đã ngưng khai thác thì không đánh dấu bảo trì.
        resource.setStatus(ResourceStatus.INACTIVE);
        log.input("id = {}", ID);

        DomainException thrown = assertThrows(DomainException.class, () -> service.markMaintenance(ID));

        log.output("code = {}, status = {}", thrown.code(), resource.getStatus());
        assertEquals("RESOURCE_INACTIVE", thrown.code());
        assertEquals(ResourceStatus.INACTIVE, resource.getStatus());
        verify(resources, never()).save(any());
    }

    // Viewpoint: VP7
    @Test
    public void test_markMaintenance_NotFound_010() {
        // Không có vị trí.
        log.input("id = {}", 99L);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.markMaintenance(99L));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // ---------- reactivate ----------

    // Viewpoint: VP3
    @Test
    public void test_reactivate_FromMaintenance_011() {
        // Mở lại vị trí đang bảo trì.
        resource.setStatus(ResourceStatus.MAINTENANCE);
        log.input("id = {}", ID);

        SpaceResource result = service.reactivate(ID);

        log.output("status = {}", result.getStatus());
        assertEquals(ResourceStatus.ACTIVE, result.getStatus());
        assertSame(resource, result);
    }

    // Viewpoint: VP7
    @Test
    public void test_reactivate_NotFound_012() {
        // Không có vị trí.
        log.input("id = {}", 99L);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.reactivate(99L));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // ---------- markMaintenance: báo khách bị ảnh hưởng (notification.md) ----------

    // Viewpoint: VP4, VP8
    @Test
    public void test_markMaintenance_NotifiesManyAffectedBookings_013() {
        // Vòng lặp > 1 record: mỗi đơn CONFIRMED chưa kết thúc của vị trí nhận một tin; tìm theo giờ hiện tại.
        Booking first = new Booking();
        Booking second = new Booking();
        when(bookings.findByResourceIdAndStatusAndPeriodEndAtAfterOrderByPeriodStartAtAsc(ID, BookingStatus.CONFIRMED, NOW))
                .thenReturn(List.of(first, second));
        log.input("id = {}", ID);

        SpaceResource result = service.markMaintenance(ID);

        log.output("status = {}", result.getStatus());
        assertEquals(ResourceStatus.MAINTENANCE, result.getStatus());
        verify(notifications).resourceMaintenance(first);
        verify(notifications).resourceMaintenance(second);
    }

    // Viewpoint: VP4
    @Test
    public void test_markMaintenance_NotifiesOneAffectedBooking_014() {
        // Vòng lặp 1 record.
        Booking only = new Booking();
        when(bookings.findByResourceIdAndStatusAndPeriodEndAtAfterOrderByPeriodStartAtAsc(ID, BookingStatus.CONFIRMED, NOW))
                .thenReturn(List.of(only));
        log.input("id = {}", ID);

        SpaceResource result = service.markMaintenance(ID);

        log.output("status = {}", result.getStatus());
        assertEquals(ResourceStatus.MAINTENANCE, result.getStatus());
        verify(notifications).resourceMaintenance(only);
    }

    // Viewpoint: VP4
    @Test
    public void test_markMaintenance_NoAffectedBookings_015() {
        // Vòng lặp 0 record: không gửi tin nào.
        log.input("id = {}", ID);

        SpaceResource result = service.markMaintenance(ID);

        log.output("status = {}", result.getStatus());
        assertEquals(ResourceStatus.MAINTENANCE, result.getStatus());
        verify(notifications, never()).resourceMaintenance(any());
    }

    // Viewpoint: VP3
    @Test
    public void test_markMaintenance_AlreadyMaintenance_016() {
        // Bấm bảo trì lần hai: giữ nguyên, không lưu, không báo khách lần nữa.
        resource.setStatus(ResourceStatus.MAINTENANCE);
        log.input("id = {}", ID);

        SpaceResource result = service.markMaintenance(ID);

        log.output("status = {}", result.getStatus());
        assertSame(resource, result);
        verify(resources, never()).save(any());
        verify(notifications, never()).resourceMaintenance(any());
    }
}
