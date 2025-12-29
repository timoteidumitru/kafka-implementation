package com.kafka_implementation.events.inventory;

import com.kafka_implementation.events.base.*;

import java.time.Instant;
import java.util.UUID;

public record InventoryReserveRequestedEvent(
        EventMetadata metadata,
        UUID orderId,
        UUID productId,
        int quantity
) implements DomainEvent {

    @Override
    public EventType getEventType() {
        return EventType.PAYMENT_FAILED;
    }

    @Override
    public int getVersion() {
        return metadata.version();
    }
}

