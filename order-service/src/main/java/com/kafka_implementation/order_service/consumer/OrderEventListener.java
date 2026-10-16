package com.kafka_implementation.order_service.consumer;

import com.kafka_implementation.order_service.service.OrderEventProcessor;
import com.kafka_implementation.shared_events.inventory.InventoryReservationFailedEvent;
import com.kafka_implementation.shared_events.inventory.InventoryReservedEvent;
import com.kafka_implementation.shared_events.payment.PaymentFailedEvent;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

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
            MDC.clear();
        }
    }

    @KafkaListener(
            topics = INVENTORY_EVENTS_V1,
            groupId = "order-service"
    )
    public void onInventoryFailed(
            InventoryReservationFailedEvent event
    ) {
        MDC.put("eventId", event.metadata().eventId().toString());
        MDC.put("orderId", event.orderId().toString());

        try {
            orderEventProcessor.processInventoryFailure(event);
        } finally {
            MDC.clear();
        }
    }

    @KafkaListener(
            topics = INVENTORY_EVENTS_V1,
            groupId = "order-service"
    )
    public void onInventoryReserved(InventoryReservedEvent event) {

        MDC.put("eventId", event.metadata().eventId().toString());
        MDC.put("orderId", event.orderId().toString());

        try {
            orderEventProcessor.processInventoryReserved(event);
        } finally {
            MDC.clear();
        }
    }

}