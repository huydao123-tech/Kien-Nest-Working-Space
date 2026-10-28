package com.cowork.shared.application;

import com.cowork.shared.domain.AggregateRoot;

public interface DomainEventPublisher {

    /** Phát các event đang chờ của aggregate. Listener AFTER_COMMIT chỉ chạy khi giao dịch commit thành công. */
    void publishEventsOf(AggregateRoot aggregate);
}
