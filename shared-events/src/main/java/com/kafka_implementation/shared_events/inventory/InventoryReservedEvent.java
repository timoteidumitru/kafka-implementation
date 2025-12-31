package com.kafka_implementation.shared_events.inventory;

import com.kafka_implementation.shared_events.base.DomainEvent;
import com.kafka_implementation.shared_events.base.EventMetadata;
import com.kafka_implementation.shared_events.base.EventType;

import java.util.UUID;

public record InventoryReservedEvent(
        EventMetadata metadata,
        UUID orderId,
        UUID productId,
        int quantity
) implements DomainEvent {

    @Override
    public EventType getEventType() {
        return EventType.INVENTORY_RESERVED;
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



