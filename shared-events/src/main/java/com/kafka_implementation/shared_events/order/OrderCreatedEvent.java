package com.kafka_implementation.shared_events.order;

import com.kafka_implementation.shared_events.base.*;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderCreatedEvent(
        EventMetadata metadata,
        UUID orderId,
        UUID userId,
        UUID productId,
        int quantity,
        BigDecimal price
) implements DomainEvent {

    @Override
    public EventType getEventType() {
        return EventType.ORDER_CREATED;
    }

    @Override
    public int getVersion() {
        return metadata.version();
    }
}
