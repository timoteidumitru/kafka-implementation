package com.kafka_implementation.shared_events.base;

import java.util.UUID;

public interface DomainEvent {

    EventMetadata metadata();

    EventType getEventType();

    int getVersion();

    UUID getAggregateId();
}
