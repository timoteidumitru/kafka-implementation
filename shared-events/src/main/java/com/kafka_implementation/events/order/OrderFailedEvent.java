package com.kafka_implementation.events.order;

import com.kafka_implementation.events.base.DomainEvent;
import com.kafka_implementation.events.base.EventMetadata;
import com.kafka_implementation.events.base.EventType;
import java.util.UUID;

public record OrderFailedEvent(
        EventMetadata metadata,
        UUID orderId,
        String reason
) implements DomainEvent {

    @Override
    public EventType getEventType() {
        return EventType.ORDER_FAILED;
    }

    @Override
    public int getVersion() {
        return metadata.version();
    }
}
