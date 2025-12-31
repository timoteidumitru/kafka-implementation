package com.kafka_implementation.shared_events.inventory;

import com.kafka_implementation.shared_events.base.*;
import java.util.UUID;

public record InventoryReserveRequestedEvent(
        EventMetadata metadata,
        UUID orderId,
        UUID productId,
        int quantity
) implements DomainEvent {

    @Override
    public EventType getEventType() {
        return EventType.INVENTORY_RESERVE_REQUESTED;
    }

    @Override
    public int getVersion() {
        return metadata.version();
    }

    @Override
    public UUID getAggregateId() {
        return orderId;
    }
}


