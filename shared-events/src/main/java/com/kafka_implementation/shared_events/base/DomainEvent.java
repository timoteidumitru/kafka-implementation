package com.kafka_implementation.shared_events.base;


public interface DomainEvent {

    EventMetadata metadata();

    EventType getEventType();

    int getVersion();
}
