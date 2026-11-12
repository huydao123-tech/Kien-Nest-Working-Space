package com.cowork.dto;

import com.cowork.dataaccess.entity.Amenity;
import com.cowork.dataaccess.entity.Branch;
import com.cowork.dataaccess.entity.PriceRule;
import com.cowork.dataaccess.entity.ResourceType;
import com.cowork.dataaccess.entity.SpaceResource;
import com.cowork.dataaccess.entity.Zone;
import com.cowork.dataaccess.enums.BranchStatus;
import com.cowork.dataaccess.enums.DayType;
import com.cowork.dataaccess.enums.ResourceStatus;
import com.cowork.dataaccess.enums.ResourceTypeCode;
import com.cowork.dto.AvailabilityDtos.ResourceAmenityResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;

/** Quản trị danh mục không gian và bảng giá (UC19, UC20): chi nhánh, khu vực, loại chỗ, vị trí, tiện ích, giá. */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record BranchRequest(@NotBlank @Size(max = 20) String code,
                                @NotBlank @Size(max = 255) String name,
                                @NotBlank @Size(max = 500) String address,
                                @NotNull LocalTime openTime,
                                @NotNull LocalTime closeTime,
                                BranchStatus status) {
    }

    public record BranchDetail(Long id, String code, String name, String address, LocalTime openTime, LocalTime closeTime,
                               BranchStatus status) {

        public static BranchDetail from(Branch b) {
            return new BranchDetail(b.getId(), b.getCode(), b.getName(), b.getAddress(), b.getOpenTime(), b.getCloseTime(),
                    b.getStatus());
        }
    }

    public record ZoneRequest(@NotNull Long branchId,
                              @NotBlank @Size(max = 100) String name,
                              @Min(0) int floor,
                              @Size(max = 500) String floorPlanUrl) {
    }

    public record ZoneDetail(Long id, Long branchId, String name, int floor, String floorPlanUrl) {

        public static ZoneDetail from(Zone z) {
            return new ZoneDetail(z.getId(), z.getBranch().getId(), z.getName(), z.getFloor(), z.getFloorPlanUrl());
        }
    }

    public record ResourceTypeRequest(@NotNull ResourceTypeCode code,
                                      @NotBlank @Size(max = 100) String name,
                                      String description) {
    }

    public record ResourceTypeDetail(Long id, ResourceTypeCode code, String name, String description) {

        public static ResourceTypeDetail from(ResourceType t) {
            return new ResourceTypeDetail(t.getId(), t.getCode(), t.getName(), t.getDescription());
        }
    }

    /** Tiện ích trang bị cho vị trí: mã tiện ích + số lượng. */
    public record AmenityQuantity(@NotBlank String code, @Min(1) int quantity) {
    }

    /** {@code status} bỏ trống khi tạo = ACTIVE, khi sửa = giữ nguyên. {@code amenities} bỏ trống khi sửa = giữ nguyên. */
    public record SpaceResourceRequest(@NotBlank @Size(max = 20) String code,
                                       @NotBlank @Size(max = 255) String name,
                                       @NotNull ResourceTypeCode type,
                                       @Min(1) int capacity,
                                       ResourceStatus status,
                                       Long zoneId,
                                       @Min(0) @Max(100) Integer posX,
                                       @Min(0) @Max(100) Integer posY,
                                       List<@Valid AmenityQuantity> amenities) {
    }

    public record SpaceResourceDetail(Long id, String code, String name, ResourceTypeCode type, int capacity,
                                      ResourceStatus status, Long zoneId, Integer posX, Integer posY,
                                      List<ResourceAmenityResponse> amenities) {

        public static SpaceResourceDetail from(SpaceResource r) {
            return new SpaceResourceDetail(r.getId(), r.getCode(), r.getName(), r.getType(), r.getCapacity(), r.getStatus(),
                    r.getZone() == null ? null : r.getZone().getId(), r.getPosX(), r.getPosY(), ResourceAmenityResponse.of(r));
        }
    }

    public record AmenityRequest(@NotBlank @Size(max = 30) String code, @NotBlank @Size(max = 100) String name) {
    }

    public record AmenityDetail(Long id, String code, String name) {

        public static AmenityDetail from(Amenity a) {
            return new AmenityDetail(a.getId(), a.getCode(), a.getName());
        }
    }

    /** Khung giờ tính bằng phút trong ngày, nửa mở [startMinute, endMinute): 540 = 09:00, 1440 = hết ngày. */
    public record PriceRuleRequest(@NotNull ResourceTypeCode type,
                                   @NotNull DayType dayType,
                                   @Min(0) @Max(1439) int startMinute,
                                   @Min(1) @Max(1440) int endMinute,
                                   @Min(0) long pricePerBlock,
                                   @NotBlank @Size(max = 50) String label) {
    }

    public record PriceRuleDetail(Long id, ResourceTypeCode type, DayType dayType, int startMinute, int endMinute,
                                  long pricePerBlock, String label) {

        public static PriceRuleDetail from(PriceRule p) {
            return new PriceRuleDetail(p.getId(), p.getResourceType().getCode(), p.getDayType(), p.getStartMinute(),
                    p.getEndMinute(), p.getPricePerBlock(), p.getLabel());
        }
    }
}
