package com.cowork.presentation.controller;

import com.cowork.business.service.BookingService;
import com.cowork.business.service.BookingService.RescheduleResult;
import com.cowork.dataaccess.enums.BookingStatus;
import com.cowork.dataaccess.valueobject.Money;
import com.cowork.dto.Actor;
import com.cowork.dto.BookingDtos.BookingFilter;
import com.cowork.dto.BookingDtos.BookingResponse;
import com.cowork.dto.BookingDtos.BookingSummary;
import com.cowork.dto.BookingDtos.CancelResponse;
import com.cowork.dto.BookingDtos.ExtendRequest;
import com.cowork.dto.BookingDtos.HoldBookingRequest;
import com.cowork.dto.BookingDtos.PageResponse;
import com.cowork.dto.BookingDtos.QrRequest;
import com.cowork.dto.BookingDtos.RescheduleRequest;
import com.cowork.dto.BookingDtos.RescheduleResponse;
import com.cowork.dto.BookingDtos.SettleRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;


@RestController
@RequestMapping("/api")
class BookingController {

    private final BookingService bookingService;

    BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** UC03: công khai. Gửi kèm token khách thì đơn thuộc tài khoản đó và được dùng giờ membership. */
    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    BookingResponse hold(@Valid @RequestBody HoldBookingRequest request, @AuthenticationPrincipal Jwt jwt) {
        return BookingResponse.from(bookingService.hold(request, Roles.actorOf(jwt)));
    }

    @GetMapping("/bookings/{id}")
    @PreAuthorize(Roles.FRONT_DESK_OR_CUSTOMER)
    BookingResponse get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return BookingResponse.from(bookingService.get(id, Roles.actorOf(jwt)));
    }

    /** Nhân viên quầy xem toàn bộ đơn, lọc theo khách ({@code customerId} hoặc {@code phone}), trạng thái, giờ bắt đầu. */
    @GetMapping("/bookings")
    @PreAuthorize(Roles.FRONT_DESK)
    PageResponse<BookingSummary> search(@RequestParam(required = false) Long customerId,
                                        @RequestParam(required = false) String phone,
                                        @RequestParam(required = false) BookingStatus status,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        BookingFilter filter = new BookingFilter(customerId, phone, status, from, to);
        return PageResponse.of(bookingService.search(filter, page, size), BookingSummary::from);
    }

    /** "Đơn của tôi" — khách đã đăng nhập. */
    @GetMapping("/me/bookings")
    @PreAuthorize(Roles.CUSTOMER)
    PageResponse<BookingSummary> mine(@RequestParam(required = false) BookingStatus status,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size,
                                      @AuthenticationPrincipal Jwt jwt) {
        return PageResponse.of(bookingService.listMine(Roles.actorOf(jwt), status, page, size), BookingSummary::from);
    }

    @PostMapping("/bookings/{id}/cancel")
    @PreAuthorize(Roles.FRONT_DESK_OR_CUSTOMER)
    CancelResponse cancel(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Actor actor = Roles.actorOf(jwt);
        Money refund = bookingService.cancel(id, actor);
        return new CancelResponse(id, bookingService.get(id, actor).getStatus(), refund.amount());
    }

    @PostMapping("/bookings/{id}/reschedule")
    @PreAuthorize(Roles.FRONT_DESK_OR_CUSTOMER)
    RescheduleResponse reschedule(@PathVariable Long id, @Valid @RequestBody RescheduleRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        RescheduleResult result = bookingService.reschedule(id, request, Roles.actorOf(jwt));
        return new RescheduleResponse(BookingResponse.from(result.booking()), result.paymentRequired(),
                result.amountDue().amount(), result.refund().amount());
    }

    @PostMapping("/bookings/{id}/extend")
    @PreAuthorize(Roles.FRONT_DESK)
    BookingResponse extend(@PathVariable Long id, @Valid @RequestBody ExtendRequest request) {
        return BookingResponse.from(bookingService.extend(id, request.newEndAt()));
    }

    @PostMapping("/bookings/{id}/settle")
    @PreAuthorize(Roles.FRONT_DESK)
    BookingResponse settle(@PathVariable Long id, @Valid @RequestBody SettleRequest request) {
        return BookingResponse.from(bookingService.settleOutstanding(id, Money.vnd(request.amount())));
    }

    @PostMapping("/check-ins")
    @PreAuthorize(Roles.FRONT_DESK)
    BookingResponse checkIn(@Valid @RequestBody QrRequest request) {
        return BookingResponse.from(bookingService.checkIn(request.qrToken()));
    }

    @PostMapping("/check-outs")
    @PreAuthorize(Roles.FRONT_DESK)
    BookingResponse checkOut(@Valid @RequestBody QrRequest request) {
        return BookingResponse.from(bookingService.checkOut(request.qrToken()));
    }
}
