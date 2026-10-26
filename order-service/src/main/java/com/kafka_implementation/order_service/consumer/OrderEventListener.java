package com.kafka_implementation.order_service.consumer;

import com.kafka_implementation.order_service.service.OrderEventProcessor;
import com.kafka_implementation.shared_events.base.DomainEvent;
import com.kafka_implementation.shared_events.inventory.InventoryReservationFailedEvent;
import com.kafka_implementation.shared_events.inventory.InventoryReservedEvent;
import com.kafka_implementation.shared_events.payment.PaymentFailedEvent;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static com.kafka_implementation.shared_events.topics.Topics.*;

@Component
public class OrderEventListener {

    private final OrderEventProcessor orderEventProcessor;

    public OrderEventListener(
            OrderEventProcessor orderEventProcessor
    ) {
        this.orderEventProcessor = orderEventProcessor;
    }

    @KafkaListener(
            topics = PAYMENT_EVENTS_V1,
            groupId = "order-service"
    )
    public void onPaymentFailed(PaymentFailedEvent event) {

        MDC.put("eventId", event.metadata().eventId().toString());
        MDC.put("orderId", event.orderId().toString());

        try {
            orderEventProcessor.processPaymentFailure(event);
        } finally {
            MDC.remove("eventId");
            MDC.remove("orderId");
        }
    }

    @KafkaListener(
            topics = INVENTORY_EVENTS_V1,
            groupId = "order-service"
    )
    public void onInventoryEvent(DomainEvent event) {

        Objects.requireNonNull(event, "event must not be null");

        MDC.put("eventId", event.metadata().eventId().toString());
        MDC.put("orderId", event.getAggregateId().toString());

        try {

            if (event instanceof InventoryReservedEvent reserved) {
                orderEventProcessor.processInventoryReserved(reserved);
                return;
            }

            if (event instanceof InventoryReservationFailedEvent failed) {
                orderEventProcessor.processInventoryFailure(failed);
                return;
            }

            throw new IllegalArgumentException(
                    "Unsupported inventory event type: "
                            + event.getClass().getName()
            );

        } finally {
            MDC.remove("eventId");
            MDC.remove("orderId");
        }
    }
}
