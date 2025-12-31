package com.kafka_implementation.shared_events.order;

import com.kafka_implementation.shared_events.base.DomainEvent;
import com.kafka_implementation.shared_events.base.EventMetadata;
import com.kafka_implementation.shared_events.base.EventType;
import java.util.UUID;

public record OrderCompletedEvent(
        EventMetadata metadata,
        UUID orderId
) implements DomainEvent {

    @Override
    public EventType getEventType() {
        return EventType.ORDER_COMPLETED;
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

