package com.cowork.shared.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Gốc của mọi aggregate: gom các domain event phát sinh trong một thao tác nghiệp vụ
 * để application service phát đi sau khi lưu.
 */
public abstract class AggregateRoot {

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }
}
