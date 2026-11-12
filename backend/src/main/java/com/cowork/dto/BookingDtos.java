package com.cowork.dto;

import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.BookingAddon;
import com.cowork.dataaccess.entity.BookingCharge;
import com.cowork.dataaccess.enums.BookingStatus;
import com.cowork.dataaccess.enums.ChargeType;
import com.cowork.dataaccess.valueobject.ContactInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

public final class BookingDtos {

    private BookingDtos() {
    }

    public record ContactRequest(@NotBlank String fullName, @NotBlank String phone, String email) {

        public ContactInfo toDomain() {
            return new ContactInfo(fullName, phone, email);
        }
    }

    /** FR-BOOK-02: một add-on thuê kèm và số lượng. */
    public record AddonSelection(@NotNull Long addonId, @Min(1) int quantity) {
    }

    /**
     * UC03 giữ chỗ.
     * <ul>
     *   <li>{@code contact}: bắt buộc với khách vãng lai; khách đã đăng nhập thì bỏ trống (dùng hồ sơ tài khoản).</li>
     *   <li>{@code addons}: add-on thuê kèm, không bắt buộc.</li>
     *   <li>{@code voucherCode}: mã voucher (BR-12), không bắt buộc.</li>
     *   <li>{@code useMembership}: trừ giờ gói thành viên (BR-13), chỉ khách đã đăng nhập; không dùng chung với voucher (Q8).</li>
     * </ul>
     */
    public record HoldBookingRequest(@NotNull Long resourceId,
                                     @NotNull LocalDateTime startAt,
                                     @NotNull LocalDateTime endAt,
                                     @Min(1) int partySize,
                                     @Valid ContactRequest contact,
                                     List<@Valid @NotNull AddonSelection> addons,
                                     @Size(max = 30) String voucherCode,
                                     boolean useMembership) {

        /** Đặt chỗ không add-on, không voucher, không membership (luồng cũ). */
        public HoldBookingRequest(Long resourceId, LocalDateTime startAt, LocalDateTime endAt, int partySize,
                                  ContactRequest contact) {
            this(resourceId, startAt, endAt, partySize, contact, List.of(), null, false);
        }
    }

    public record ExtendRequest(@NotNull LocalDateTime newEndAt) {
    }

    /**
     * Đổi chỗ trước check-in: khung giờ mới (được đổi thời lượng) và vị trí mới.
     * {@code resourceId} bỏ trống = giữ vị trí hiện tại.
     */
    public record RescheduleRequest(Long resourceId, @NotNull LocalDateTime startAt, @NotNull LocalDateTime endAt) {
    }

    public record SettleRequest(@Min(1) long amount) {
    }

    public record QrRequest(@NotBlank String qrToken) {
    }

    public record CancelResponse(Long bookingId, BookingStatus status, long refundAmount) {
    }

    /**
     * Kết quả đổi chỗ.
     * <ul>
     *   <li>{@code paymentRequired = false}: đã đổi xong; {@code refundAmount} > 0 khi chỗ mới rẻ hơn số đã trả.</li>
     *   <li>{@code paymentRequired = true}: chỗ mới đắt hơn, đang được giữ tới {@code booking.pendingChange.expiresAt};
     *       khách trả {@code amountDue} qua {@code POST /api/bookings/{id}/payments}, trả xong đơn mới đổi.</li>
     * </ul>
     */
    public record RescheduleResponse(BookingResponse booking, boolean paymentRequired, long amountDue, long refundAmount) {
    }

    /** Yêu cầu đổi chỗ đang chờ thanh toán phần chênh {@code amountDue}. */
    public record PendingChangeResponse(Long resourceId, LocalDateTime startAt, LocalDateTime endAt, long amountDue,
                                        LocalDateTime expiresAt) {

        public static PendingChangeResponse from(Booking b) {
            if (!b.hasPendingChange()) {
                return null;
            }
            return new PendingChangeResponse(b.getPendingResource().getId(), b.getPendingStartAt(), b.getPendingEndAt(),
                    b.getPendingAmount(), b.getPendingExpiresAt());
        }
    }

    /** Bộ lọc danh sách đơn cho nhân viên; trường nào rỗng thì không lọc theo trường đó. {@code from}/{@code to} lọc theo giờ bắt đầu. */
    public record BookingFilter(Long customerId, String phone, BookingStatus status, LocalDateTime from, LocalDateTime to) {
    }

    public record ChargeResponse(ChargeType type, long amount, LocalDateTime startAt, LocalDateTime endAt) {

        public static ChargeResponse from(BookingCharge c) {
            return new ChargeResponse(c.getType(), c.getAmount(), c.getStartAt(), c.getEndAt());
        }
    }

    /** Add-on thuê kèm; {@code unitPrice} là đơn giá một block tại lúc đặt. */
    public record BookingAddonResponse(Long addonId, String code, String name, int quantity, long unitPrice) {

        public static BookingAddonResponse from(BookingAddon a) {
            return new BookingAddonResponse(a.getAddon().getId(), a.getAddon().getCode(), a.getAddon().getName(),
                    a.getQuantity(), a.getUnitPrice());
        }
    }

    public record BookingResponse(Long id,
                                  String code,
                                  String qrToken,
                                  Long customerId,
                                  Long resourceId,
                                  LocalDateTime startAt,
                                  LocalDateTime endAt,
                                  int partySize,
                                  BookingStatus status,
                                  LocalDateTime holdExpiresAt,
                                  long totalAmount,
                                  long paidAmount,
                                  long outstandingAmount,
                                  List<ChargeResponse> charges,
                                  List<BookingAddonResponse> addons,
                                  LocalDateTime checkedInAt,
                                  LocalDateTime checkedOutAt,
                                  PendingChangeResponse pendingChange) {

        public static BookingResponse from(Booking b) {
            return new BookingResponse(b.getId(), b.getCode(), b.getQrToken(), b.getCustomer().getId(), b.getResource().getId(),
                    b.getPeriod().startAt(), b.getPeriod().endAt(), b.getPartySize(), b.getStatus(), b.getHoldExpiresAt(),
                    b.getTotal().amount(), b.getPaid().amount(), b.getOutstanding().amount(),
                    b.getCharges().stream().map(ChargeResponse::from).toList(),
                    b.getAddons().stream().map(BookingAddonResponse::from).toList(),
                    b.getCheckedInAt(), b.getCheckedOutAt(), PendingChangeResponse.from(b));
        }
    }

    /** Một dòng trong danh sách đơn (không có mã QR). Khách và vị trí được nạp kèm bởi {@code BookingRepository.findAll}. */
    public record BookingSummary(Long id,
                                 String code,
                                 Long customerId,
                                 String customerName,
                                 String customerPhone,
                                 Long resourceId,
                                 String resourceCode,
                                 LocalDateTime startAt,
                                 LocalDateTime endAt,
                                 BookingStatus status,
                                 long totalAmount,
                                 long paidAmount,
                                 LocalDateTime createdAt) {

        public static BookingSummary from(Booking b) {
            return new BookingSummary(b.getId(), b.getCode(), b.getCustomer().getId(), b.getCustomer().getContact().fullName(),
                    b.getCustomer().getContact().phone(), b.getResource().getId(), b.getResource().getCode(),
                    b.getPeriod().startAt(), b.getPeriod().endAt(), b.getStatus(), b.getTotal().amount(),
                    b.getPaid().amount(), b.getCreatedAt());
        }
    }

    /** Một trang kết quả; {@code page} bắt đầu từ 0. */
    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

        public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
            return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }
}
