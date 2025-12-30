package com.kafka_implementation.shared_events.payment;

import com.kafka_implementation.shared_events.base.*;
import java.util.UUID;

public record PaymentCompletedEvent(
        EventMetadata metadata,
        UUID orderId,
        UUID productId,
        int quantity,
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



