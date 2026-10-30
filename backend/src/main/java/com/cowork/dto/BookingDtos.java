package com.cowork.dto;

import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.BookingCharge;
import com.cowork.dataaccess.entity.BookingStatus;
import com.cowork.dataaccess.entity.ChargeType;
import com.cowork.dataaccess.entity.ContactInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class BookingDtos {

    private BookingDtos() {
    }

    public record ContactRequest(@NotBlank String fullName, @NotBlank String phone, String email) {

        public ContactInfo toDomain() {
            return new ContactInfo(fullName, phone, email);
        }
    }

    public record HoldBookingRequest(@NotNull UUID resourceId,
                              @NotNull LocalDateTime startAt,
                              @NotNull LocalDateTime endAt,
                              @Min(1) int partySize,
                              @NotNull @Valid ContactRequest contact) {
    }

    public record ExtendRequest(@NotNull LocalDateTime newEndAt) {
    }

    public record SettleRequest(@Min(1) long amount) {
    }

    public record QrRequest(@NotBlank String qrToken) {
    }

    public record CancelResponse(UUID bookingId, BookingStatus status, long refundAmount) {
    }

    public record ChargeResponse(ChargeType type, long amount, LocalDateTime startAt, LocalDateTime endAt) {

        public static ChargeResponse from(BookingCharge c) {
            return new ChargeResponse(c.type(), c.amount(), c.startAt(), c.endAt());
        }
    }

    public record BookingResponse(UUID id,
                           String code,
                           String qrToken,
                           UUID customerId,
                           UUID resourceId,
                           LocalDateTime startAt,
                           LocalDateTime endAt,
                           int partySize,
                           BookingStatus status,
                           LocalDateTime holdExpiresAt,
                           long totalAmount,
                           long paidAmount,
                           long outstandingAmount,
                           List<ChargeResponse> charges,
                           LocalDateTime checkedInAt,
                           LocalDateTime checkedOutAt) {

        public static BookingResponse from(Booking b) {
            return new BookingResponse(b.getId(), b.getCode(), b.getQrToken(), b.getCustomerId(), b.getResourceId(),
                    b.getPeriod().startAt(), b.getPeriod().endAt(), b.getPartySize(), b.getStatus(), b.getHoldExpiresAt(),
                    b.getTotal().amount(), b.getPaid().amount(), b.getOutstanding().amount(),
                    b.getCharges().stream().map(ChargeResponse::from).toList(),
                    b.getCheckedInAt(), b.getCheckedOutAt());
        }
    }
}
