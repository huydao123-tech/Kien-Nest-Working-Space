package com.cowork.booking.application;

import com.cowork.booking.domain.ContactInfo;

import java.time.LocalDateTime;
import java.util.UUID;

public record HoldBookingCommand(UUID resourceId, LocalDateTime startAt, LocalDateTime endAt, int partySize,
                                 ContactInfo contact) {
}
