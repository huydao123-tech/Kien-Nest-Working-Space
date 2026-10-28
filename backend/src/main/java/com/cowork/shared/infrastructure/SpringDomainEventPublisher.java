package com.cowork.shared.infrastructure;

import com.cowork.shared.application.DomainEventPublisher;
import com.cowork.shared.domain.AggregateRoot;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher publisher;

    SpringDomainEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publishEventsOf(AggregateRoot aggregate) {
        aggregate.pullDomainEvents().forEach(publisher::publishEvent);
    }
}
