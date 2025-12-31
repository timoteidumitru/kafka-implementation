package com.kafka_implementation.shared_events.base;

import java.time.Instant;
import java.util.UUID;

public final class EventMetadataFactory {

    private EventMetadataFactory() {}

    public static EventMetadata next(EventMetadata previous, String sourceService) {
        return new EventMetadata(
                UUID.randomUUID(),          // new eventId
                previous.correlationId(),   // same saga
                Instant.now(),
                sourceService,
                previous.version()
        );
    }
}
