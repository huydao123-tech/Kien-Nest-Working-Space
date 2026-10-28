package com.cowork.shared.domain;

import java.time.LocalDateTime;

public interface DomainEvent {

    LocalDateTime occurredAt();
}
