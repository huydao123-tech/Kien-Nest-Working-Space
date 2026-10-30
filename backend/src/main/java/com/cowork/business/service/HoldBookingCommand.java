package com.cowork.business.service;

import com.cowork.dataaccess.entity.ContactInfo;

import java.time.LocalDateTime;
import java.util.UUID;

public record HoldBookingCommand(UUID resourceId, LocalDateTime startAt, LocalDateTime endAt, int partySize,
                                 ContactInfo contact) {
}
