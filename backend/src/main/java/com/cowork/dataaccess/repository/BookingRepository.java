package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.enums.BookingStatus;
import com.cowork.dataaccess.valueobject.TimeRange;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Lọc danh sách đơn có nhiều điều kiện tùy chọn (khách, trạng thái, khoảng ngày) nên dùng {@link Specification}
 * thay cho JPQL kiểu "(:param is null or ...)" — dạng đó dễ lỗi kiểu tham số null trên PostgreSQL.
 */
public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    Optional<Booking> findByQrToken(String qrToken);

    /**
     * Danh sách đơn có phân trang; nạp kèm khách và vị trí để hiển thị tên, mã mà không phát sinh thêm câu SELECT.
     * Kiểu LOAD: các quan hệ khác giữ cách nạp khai báo trong entity (khoản thu, lịch sử, add-on vẫn EAGER).
     */
    @Override
    @EntityGraph(attributePaths = {"customer", "resource"}, type = EntityGraph.EntityGraphType.LOAD)
    Page<Booking> findAll(Specification<Booking> spec, Pageable pageable);

    /** BR-01: có đơn nào (khác {@code excludeBookingId}) đang chiếm vị trí trong khoảng {@code range} không. */
    default boolean existsOccupying(Long resourceId, TimeRange range, Long excludeBookingId) {
        // "id <> null" trong SQL luôn cho kết quả UNKNOWN, nên khi không loại trừ đơn nào thì dùng id 0 (id tự tăng bắt đầu từ 1).
        Long exclude = excludeBookingId == null ? 0L : excludeBookingId;
        return existsOverlap(resourceId, range.startAt(), range.endAt(), BookingStatus.SLOT_OCCUPYING, exclude)
                || existsPendingOverlap(resourceId, range.startAt(), range.endAt(), exclude);
    }

    /**
     * Trong các vị trí {@code resourceIds}, id các vị trí đang bị giữ cho một yêu cầu đổi chỗ chờ thanh toán
     * (booking.md Prompt 2) trong khoảng {@code range}. Sơ đồ chỗ hiển thị như đang giữ chỗ.
     */
    default List<Long> findPendingResourceIds(Collection<Long> resourceIds, TimeRange range) {
        if (resourceIds.isEmpty()) {
            return List.of();
        }
        return findPendingOccupancy(resourceIds, range.startAt(), range.endAt());
    }

    /** Đơn có yêu cầu đổi chỗ đã quá hạn thanh toán — cho job hủy yêu cầu đổi. */
    @Query("select b.id from Booking b where b.pendingExpiresAt <= :now")
    List<Long> findPendingChangeIdsExpiredAt(@Param("now") LocalDateTime now);

    /**
     * Chỗ mới của yêu cầu đổi chỗ đang chờ thanh toán cũng chiếm vị trí (BR-01). Cột pending không nằm trong
     * ràng buộc {@code ex_booking_no_overlap}, nên chỉ được bảo vệ bằng khóa dòng vị trí ở tầng service.
     */
    @Query("""
            select count(b) > 0 from Booking b
            where b.pendingResource.id = :resourceId
              and b.pendingExpiresAt is not null
              and b.pendingStartAt < :endAt
              and b.pendingEndAt > :startAt
              and b.id <> :excludeId
            """)
    boolean existsPendingOverlap(@Param("resourceId") Long resourceId,
                                 @Param("startAt") LocalDateTime startAt,
                                 @Param("endAt") LocalDateTime endAt,
                                 @Param("excludeId") Long excludeId);

    @Query("""
            select b.pendingResource.id from Booking b
            where b.pendingResource.id in :resourceIds
              and b.pendingExpiresAt is not null
              and b.pendingStartAt < :endAt
              and b.pendingEndAt > :startAt
            """)
    List<Long> findPendingOccupancy(@Param("resourceIds") Collection<Long> resourceIds,
                                    @Param("startAt") LocalDateTime startAt,
                                    @Param("endAt") LocalDateTime endAt);

    /** Trong các vị trí {@code resourceIds}, các đơn đang chiếm khoảng {@code range}: chỉ lấy id vị trí và trạng thái đơn. */
    default List<Occupancy> findOccupancies(Collection<Long> resourceIds, TimeRange range) {
        if (resourceIds.isEmpty()) {
            return List.of();
        }
        return findOccupancy(resourceIds, range.startAt(), range.endAt(), BookingStatus.SLOT_OCCUPYING);
    }

    /** Đơn đang giữ chỗ đã quá hạn — cho job giải phóng chỗ. */
    default List<Long> findHeldIdsExpiredAt(LocalDateTime now) {
        return findIdsByStatusAndHoldExpiredAt(BookingStatus.HELD, now);
    }

    /** Đơn đang check-in đã quá giờ kết thúc — cho job đánh dấu quá giờ. */
    default List<Long> findCheckedInIdsEndedBefore(LocalDateTime threshold) {
        return findIdsByStatusAndEndBefore(BookingStatus.CHECKED_IN, threshold);
    }

    @Query("""
            select count(b) > 0 from Booking b
            where b.resource.id = :resourceId
              and b.status in :statuses
              and b.period.startAt < :endAt
              and b.period.endAt > :startAt
              and b.id <> :excludeId
            """)
    boolean existsOverlap(@Param("resourceId") Long resourceId,
                          @Param("startAt") LocalDateTime startAt,
                          @Param("endAt") LocalDateTime endAt,
                          @Param("statuses") Collection<BookingStatus> statuses,
                          @Param("excludeId") Long excludeId);

    @Query("""
            select b.resource.id as resourceId, b.status as status from Booking b
            where b.resource.id in :resourceIds
              and b.status in :statuses
              and b.period.startAt < :endAt
              and b.period.endAt > :startAt
            """)
    List<Occupancy> findOccupancy(@Param("resourceIds") Collection<Long> resourceIds,
                                  @Param("startAt") LocalDateTime startAt,
                                  @Param("endAt") LocalDateTime endAt,
                                  @Param("statuses") Collection<BookingStatus> statuses);

    /** Một đơn đang chiếm vị trí: chỉ gồm hai cột cần cho sơ đồ, không nạp cả đơn (tránh nạp kèm khoản thu, lịch sử). */
    interface Occupancy {

        Long getResourceId();

        BookingStatus getStatus();
    }

    /** Đơn đã thanh toán mà giờ bắt đầu đã qua — ứng viên cho job đánh dấu không đến (BR-16). */
    default List<Long> findConfirmedIdsStartedBefore(LocalDateTime now) {
        return findIdsByStatusAndStartBefore(BookingStatus.CONFIRMED, now);
    }

    /** Đơn đang check-in chưa tới giờ kết thúc — ứng viên cho job cảnh báo sắp hết giờ (FR-NOTI-03). */
    default List<Long> findCheckedInIdsEndingAfter(LocalDateTime now) {
        return findIdsByStatusAndEndAfter(BookingStatus.CHECKED_IN, now);
    }

    /** FR-OPS-07: đơn ở trạng thái {@code status} của một vị trí, chưa kết thúc tính tới {@code now}, sớm nhất trước. */
    List<Booking> findByResourceIdAndStatusAndPeriodEndAtAfterOrderByPeriodStartAtAsc(Long resourceId, BookingStatus status,
                                                                                     LocalDateTime now);

    @Query("select b.id from Booking b where b.status = :status and b.period.startAt < :threshold")
    List<Long> findIdsByStatusAndStartBefore(@Param("status") BookingStatus status, @Param("threshold") LocalDateTime threshold);

    @Query("select b.id from Booking b where b.status = :status and b.period.endAt > :threshold")
    List<Long> findIdsByStatusAndEndAfter(@Param("status") BookingStatus status, @Param("threshold") LocalDateTime threshold);

    @Query("select b.id from Booking b where b.status = :status and b.holdExpiresAt <= :now")
    List<Long> findIdsByStatusAndHoldExpiredAt(@Param("status") BookingStatus status, @Param("now") LocalDateTime now);

    @Query("select b.id from Booking b where b.status = :status and b.period.endAt < :threshold")
    List<Long> findIdsByStatusAndEndBefore(@Param("status") BookingStatus status, @Param("threshold") LocalDateTime threshold);
}
