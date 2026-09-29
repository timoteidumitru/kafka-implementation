package com.kafka_implementation.shared_events.base;

import java.time.Instant;
import java.util.UUID;

public final class EventMetadataFactory {

    private EventMetadataFactory() {}

    public static EventMetadata next(EventMetadata previous, String sourceService, int version) {
        return new EventMetadata(
                UUID.randomUUID(),
                previous.correlationId(),
                Instant.now(),
                sourceService,
                version
        );
    }
}
