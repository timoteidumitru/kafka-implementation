package com.kafka_implementation.events.payment;

import com.kafka_implementation.events.base.*;
import java.util.UUID;

public record PaymentCompletedEvent(
        EventMetadata metadata,
        UUID orderId,
        String transactionId
) implements DomainEvent {

    @Override
    public EventType getEventType() {
        return EventType.PAYMENT_COMPLETED;
    }

    @Override
    public int getVersion() {
        return metadata.version();
    }
}

