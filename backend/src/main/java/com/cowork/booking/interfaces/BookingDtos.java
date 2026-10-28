package com.cowork.booking.interfaces;

import com.cowork.booking.domain.Booking;
import com.cowork.booking.domain.BookingStatus;
import com.cowork.booking.domain.ContactInfo;
import com.cowork.shared.domain.ResourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

final class BookingDtos {

    private BookingDtos() {
    }

    record ContactRequest(@NotBlank String fullName, @NotBlank String phone, String email) {

        ContactInfo toDomain() {
            return new ContactInfo(fullName, phone, email);
        }
    }

    record HoldBookingRequest(@NotNull UUID resourceId,
                              @NotNull LocalDateTime startAt,
                              @NotNull LocalDateTime endAt,
                              @Min(1) int partySize,
                              @NotNull @Valid ContactRequest contact) {
    }

    record ExtendRequest(@NotNull LocalDateTime newEndAt) {
    }

    record SettleRequest(@Min(1) long amount) {
    }

    record QrRequest(@NotBlank String qrToken) {
    }

    record CancelResponse(UUID bookingId, BookingStatus status, long refundAmount) {
    }

    record BookingResponse(UUID id,
                           String code,
                           String qrToken,
                           UUID resourceId,
                           ResourceType resourceType,
                           LocalDateTime startAt,
                           LocalDateTime endAt,
                           int partySize,
                           BookingStatus status,
                           LocalDateTime holdExpiresAt,
                           long totalAmount,
                           long paidAmount,
                           long outstandingAmount,
                           LocalDateTime checkedInAt,
                           LocalDateTime checkedOutAt) {

        static BookingResponse from(Booking b) {
            return new BookingResponse(b.id(), b.code(), b.qrToken(), b.resourceId(), b.resourceType(),
                    b.period().startAt(), b.period().endAt(), b.partySize(), b.status(), b.holdExpiresAt(),
                    b.total().amount(), b.paid().amount(), b.outstanding().amount(),
                    b.checkedInAt(), b.checkedOutAt());
        }
    }
}
