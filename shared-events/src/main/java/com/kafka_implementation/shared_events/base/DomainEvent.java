package com.kafka_implementation.shared_events.base;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;

public interface DomainEvent {

    EventMetadata metadata();

    @JsonIgnore
    EventType getEventType();

    @JsonIgnore
    int getVersion();

    @JsonIgnore
    UUID getAggregateId();
}
