package com.kafka_implementation.shared_events.payment;

import com.kafka_implementation.shared_events.base.*;
import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequestedEvent(
        EventMetadata metadata,
        UUID orderId,
        UUID userId,
        BigDecimal amount
) implements DomainEvent {

    @Override
    public EventType getEventType() {
        return EventType.PAYMENT_REQUESTED;
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