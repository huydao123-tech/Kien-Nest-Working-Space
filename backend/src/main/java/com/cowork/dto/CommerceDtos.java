package com.cowork.dto;

import com.cowork.dataaccess.entity.Addon;
import com.cowork.dataaccess.entity.MembershipPlan;
import com.cowork.dataaccess.entity.MenuCategory;
import com.cowork.dataaccess.entity.MenuItem;
import com.cowork.dataaccess.entity.Voucher;
import com.cowork.dataaccess.enums.AddonStatus;
import com.cowork.dataaccess.enums.DiscountType;
import com.cowork.dataaccess.enums.MembershipPassStatus;
import com.cowork.dataaccess.enums.MembershipPlanStatus;
import com.cowork.dataaccess.enums.ResourceTypeCode;
import com.cowork.dataaccess.enums.VoucherStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** Quản trị add-on, thực đơn, voucher, gói thành viên (UC19, UC21). */
public final class CommerceDtos {

    private CommerceDtos() {
    }

    // ---------------- addon ----------------

    public record AddonRequest(@NotNull Long branchId,
                               @NotBlank @Size(max = 30) String code,
                               @NotBlank @Size(max = 100) String name,
                               @Min(0) int stock,
                               @Min(0) long pricePerBlock,
                               AddonStatus status) {
    }

    public record AddonDetail(Long id, Long branchId, String code, String name, int stock, long pricePerBlock,
                              AddonStatus status) {

        public static AddonDetail from(Addon a) {
            return new AddonDetail(a.getId(), a.getBranch().getId(), a.getCode(), a.getName(), a.getStock(),
                    a.getPricePerBlock(), a.getStatus());
        }
    }

    // ---------------- menu ----------------

    public record MenuCategoryRequest(@NotBlank @Size(max = 100) String name, int sortOrder) {
    }

    public record MenuCategoryDetail(Long id, String name, int sortOrder) {

        public static MenuCategoryDetail from(MenuCategory c) {
            return new MenuCategoryDetail(c.getId(), c.getName(), c.getSortOrder());
        }
    }

    public record MenuItemRequest(@NotNull Long branchId,
                                  @NotNull Long categoryId,
                                  @NotBlank @Size(max = 100) String name,
                                  @Min(0) long price,
                                  boolean available,
                                  boolean drink) {
    }

    public record MenuItemDetail(Long id, Long branchId, Long categoryId, String name, long price, boolean available,
                                 boolean drink) {

        public static MenuItemDetail from(MenuItem m) {
            return new MenuItemDetail(m.getId(), m.getBranch().getId(), m.getCategory().getId(), m.getName(), m.getPrice(),
                    m.isAvailable(), m.isDrink());
        }
    }

    // ---------------- voucher (BR-12) ----------------

    /**
     * {@code discountValue}: % (1–100) với PERCENT, số tiền VND với AMOUNT, số phút với FREE_MINUTES.
     * {@code resourceTypes} rỗng = áp dụng mọi loại chỗ. {@code totalLimit} rỗng = không giới hạn tổng lượt.
     */
    public record VoucherRequest(@NotBlank @Size(max = 30) String code,
                                 @NotBlank @Size(max = 255) String name,
                                 @NotNull DiscountType discountType,
                                 @Min(1) long discountValue,
                                 @Min(0) long minOrderAmount,
                                 @Min(1) Long maxDiscountAmount,
                                 @NotNull LocalDateTime validFrom,
                                 @NotNull LocalDateTime validTo,
                                 @Min(1) Integer totalLimit,
                                 @Min(1) int perCustomerLimit,
                                 VoucherStatus status,
                                 List<ResourceTypeCode> resourceTypes) {
    }

    public record VoucherDetail(Long id, String code, String name, DiscountType discountType, long discountValue,
                                long minOrderAmount, Long maxDiscountAmount, LocalDateTime validFrom, LocalDateTime validTo,
                                Integer totalLimit, int perCustomerLimit, int usedCount, VoucherStatus status,
                                List<ResourceTypeCode> resourceTypes) {

        public static VoucherDetail from(Voucher v) {
            return new VoucherDetail(v.getId(), v.getCode(), v.getName(), v.getDiscountType(), v.getDiscountValue(),
                    v.getMinOrderAmount(), v.getMaxDiscountAmount(), v.getValidFrom(), v.getValidTo(), v.getTotalLimit(),
                    v.getPerCustomerLimit(), v.getUsedCount(), v.getStatus(),
                    v.getResourceTypes().stream().map(t -> t.getResourceType().getCode()).sorted().toList());
        }
    }

    // ---------------- membership_plan (BR-13) ----------------

    public record MembershipPlanRequest(@NotBlank @Size(max = 100) String name,
                                        @Min(1) int totalMinutes,
                                        @Min(1) int validDays,
                                        @Min(0) long price,
                                        MembershipPlanStatus status,
                                        @NotNull @Size(min = 1) List<ResourceTypeCode> resourceTypes) {
    }

    public record MembershipPlanDetail(Long id, String name, int totalMinutes, int validDays, long price,
                                       MembershipPlanStatus status, List<ResourceTypeCode> resourceTypes) {

        public static MembershipPlanDetail from(MembershipPlan p) {
            return new MembershipPlanDetail(p.getId(), p.getName(), p.getTotalMinutes(), p.getValidDays(), p.getPrice(),
                    p.getStatus(), p.getResourceTypes().stream().map(t -> t.getResourceType().getCode()).sorted().toList());
        }
    }

    /**
     * FR-AUTH-05: một gói thành viên khách đã mua. {@code status} tính theo thời điểm xem: gói ACTIVE đã quá hạn
     * hiển thị EXPIRED. {@code usable} = còn dùng được để trừ giờ (ACTIVE, chưa hết hạn, còn phút).
     */
    public record MembershipPassResponse(Long id, String planName, int remainingMinutes, LocalDateTime purchasedAt,
                                         LocalDateTime expiresAt, MembershipPassStatus status, boolean usable) {
    }

    /** FR-AUTH-05: tổng số phút còn dùng được (cộng các gói {@code usable}) và danh sách gói, hết hạn sớm nhất trước. */
    public record MembershipSummary(int remainingMinutes, List<MembershipPassResponse> passes) {
    }
}
