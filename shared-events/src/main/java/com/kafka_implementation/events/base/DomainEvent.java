package com.kafka_implementation.events.base;


public interface DomainEvent {

    EventMetadata metadata();

    EventType getEventType();

    int getVersion();
}
