package com.kafka_implementation.shared_events.base;

import java.time.Instant;
import java.util.UUID;

public record EventMetadata(
        UUID eventId,
        UUID correlationId,
        Instant occurredAt,
        String sourceService,
        int version
) {
    public static EventMetadata create(String sourceService, int version) {
        return new EventMetadata(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now(),
                sourceService,
                version
        );
    }
}
