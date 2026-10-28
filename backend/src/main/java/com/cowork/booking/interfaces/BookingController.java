package com.cowork.booking.interfaces;

import com.cowork.booking.application.BookingService;
import com.cowork.booking.application.HoldBookingCommand;
import com.cowork.booking.domain.Booking;
import com.cowork.booking.interfaces.BookingDtos.BookingResponse;
import com.cowork.booking.interfaces.BookingDtos.CancelResponse;
import com.cowork.booking.interfaces.BookingDtos.ExtendRequest;
import com.cowork.booking.interfaces.BookingDtos.HoldBookingRequest;
import com.cowork.booking.interfaces.BookingDtos.QrRequest;
import com.cowork.booking.interfaces.BookingDtos.SettleRequest;
import com.cowork.shared.domain.Money;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
class BookingController {

    private final BookingService bookingService;

    BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    BookingResponse hold(@Valid @RequestBody HoldBookingRequest request) {
        Booking booking = bookingService.hold(new HoldBookingCommand(request.resourceId(), request.startAt(),
                request.endAt(), request.partySize(), request.contact().toDomain()));
        return BookingResponse.from(booking);
    }

    @GetMapping("/bookings/{id}")
    BookingResponse get(@PathVariable UUID id) {
        return BookingResponse.from(bookingService.get(id));
    }

    @PostMapping("/bookings/{id}/cancel")
    CancelResponse cancel(@PathVariable UUID id) {
        Money refund = bookingService.cancel(id);
        return new CancelResponse(id, bookingService.get(id).status(), refund.amount());
    }

    @PostMapping("/bookings/{id}/extend")
    BookingResponse extend(@PathVariable UUID id, @Valid @RequestBody ExtendRequest request) {
        return BookingResponse.from(bookingService.extend(id, request.newEndAt()));
    }

    @PostMapping("/bookings/{id}/settle")
    BookingResponse settle(@PathVariable UUID id, @Valid @RequestBody SettleRequest request) {
        return BookingResponse.from(bookingService.settleOutstanding(id, Money.vnd(request.amount())));
    }

    @PostMapping("/check-ins")
    BookingResponse checkIn(@Valid @RequestBody QrRequest request) {
        return BookingResponse.from(bookingService.checkIn(request.qrToken()));
    }

    @PostMapping("/check-outs")
    BookingResponse checkOut(@Valid @RequestBody QrRequest request) {
        return BookingResponse.from(bookingService.checkOut(request.qrToken()));
    }
}
