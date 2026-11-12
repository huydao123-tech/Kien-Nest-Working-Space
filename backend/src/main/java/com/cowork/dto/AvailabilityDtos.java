package com.cowork.dto;

import com.cowork.dataaccess.entity.Amenity;
import com.cowork.dataaccess.entity.Branch;
import com.cowork.dataaccess.entity.ResourceAmenity;
import com.cowork.dataaccess.entity.SpaceResource;
import com.cowork.dataaccess.entity.Zone;
import com.cowork.dataaccess.enums.ResourceTypeCode;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

/** Dữ liệu trả về của luồng tra cứu chỗ trống (UC02). */
public final class AvailabilityDtos {

    private AvailabilityDtos() {
    }

    /** Trạng thái một vị trí trên sơ đồ trong khung giờ đang xem (FR-SRCH-02). */
    public enum SlotStatus {
        /** Không có đơn nào chiếm khung giờ. */
        AVAILABLE,
        /** Có đơn đang giữ chỗ chờ thanh toán (HELD). */
        HELD,
        /** Có đơn đã xác nhận / đang dùng / quá giờ (CONFIRMED, CHECKED_IN, OVERSTAY). */
        BOOKED,
        /** Vị trí đang bảo trì, không nhận đặt (BR-09). */
        MAINTENANCE
    }

    public record BranchResponse(Long id, String code, String name, String address, LocalTime openTime, LocalTime closeTime) {

        public static BranchResponse from(Branch b) {
            return new BranchResponse(b.getId(), b.getCode(), b.getName(), b.getAddress(), b.getOpenTime(), b.getCloseTime());
        }
    }

    public record AmenityResponse(String code, String name) {

        public static AmenityResponse from(Amenity a) {
            return new AmenityResponse(a.getCode(), a.getName());
        }
    }

    /** Tiện ích của một vị trí, kèm số lượng (ví dụ 4 ổ cắm). */
    public record ResourceAmenityResponse(String code, String name, int quantity) {

        public static List<ResourceAmenityResponse> of(SpaceResource r) {
            return r.getAmenities().stream()
                    .map(ResourceAmenityResponse::from)
                    .sorted(Comparator.comparing(ResourceAmenityResponse::code))
                    .toList();
        }

        private static ResourceAmenityResponse from(ResourceAmenity ra) {
            return new ResourceAmenityResponse(ra.getAmenity().getCode(), ra.getAmenity().getName(), ra.getQuantity());
        }
    }

    /** Một vị trí còn trống trong kết quả tìm kiếm, kèm giá dự kiến cho cả khung giờ (FR-SRCH-04). */
    public record AvailableResource(Long resourceId,
                                    String code,
                                    String name,
                                    ResourceTypeCode type,
                                    int capacity,
                                    Long zoneId,
                                    String zoneName,
                                    int floor,
                                    Integer posX,
                                    Integer posY,
                                    List<ResourceAmenityResponse> amenities,
                                    PriceQuote quote) {

        public static AvailableResource from(SpaceResource r, PriceQuote quote) {
            Zone zone = r.getZone();
            return new AvailableResource(r.getId(), r.getCode(), r.getName(), r.getType(), r.getCapacity(),
                    zone.getId(), zone.getName(), zone.getFloor(), r.getPosX(), r.getPosY(),
                    ResourceAmenityResponse.of(r), quote);
        }
    }

    /** Sơ đồ mặt bằng của một chi nhánh trong khung giờ đang xem, chia theo khu vực. */
    public record FloorPlanResponse(Long branchId,
                                    String branchName,
                                    LocalTime openTime,
                                    LocalTime closeTime,
                                    LocalDateTime startAt,
                                    LocalDateTime endAt,
                                    List<ZonePlan> zones) {
    }

    public record ZonePlan(Long zoneId, String name, int floor, String floorPlanUrl, List<ResourceSlot> resources) {
    }

    /** Một vị trí trên sơ đồ: tọa độ (% ảnh sơ đồ) và trạng thái. Không chứa thông tin khách đã đặt. */
    public record ResourceSlot(Long resourceId,
                               String code,
                               String name,
                               ResourceTypeCode type,
                               int capacity,
                               Integer posX,
                               Integer posY,
                               SlotStatus status,
                               List<ResourceAmenityResponse> amenities) {

        public static ResourceSlot from(SpaceResource r, SlotStatus status) {
            return new ResourceSlot(r.getId(), r.getCode(), r.getName(), r.getType(), r.getCapacity(),
                    r.getPosX(), r.getPosY(), status, ResourceAmenityResponse.of(r));
        }
    }
}
