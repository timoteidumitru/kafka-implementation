package com.kafka_implementation.order_service.consumer;

import com.kafka_implementation.order_service.producer.OrderEventPublisher;
import com.kafka_implementation.order_service.service.OrderEventProcessor;
import com.kafka_implementation.shared_events.inventory.InventoryReservationFailedEvent;
import com.kafka_implementation.shared_events.inventory.InventoryReservedEvent;
import com.kafka_implementation.shared_events.order.OrderCompletedEvent;
import com.kafka_implementation.shared_events.order.OrderFailedEvent;
import com.kafka_implementation.shared_events.payment.PaymentFailedEvent;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.kafka_implementation.shared_events.base.EventMetadataFactory.next;
import static com.kafka_implementation.shared_events.topics.Topics.*;

@Component
public class OrderEventListener {

    private final OrderEventProcessor orderEventProcessor;
    private final OrderEventPublisher publisher;

    public OrderEventListener(
            OrderEventProcessor orderEventProcessor,
            OrderEventPublisher publisher
    ) {
        this.orderEventProcessor = orderEventProcessor;
        this.publisher = publisher;
    }

    @KafkaListener(topics = PAYMENT_EVENTS_V1, groupId = "order-service")
    public void onPaymentFailed(PaymentFailedEvent event) {
        MDC.put("eventId", event.metadata().eventId().toString());
        MDC.put("orderId", event.orderId().toString());

        try {
            boolean processed = orderEventProcessor.processCancellation(
                    event.metadata().eventId(),
                    event.orderId()
            );

            if (!processed) return;

            publisher.publishOrderFailed(
                    new OrderFailedEvent(
                            next(event.metadata(), "order-service", 1),
                            event.orderId(),
                            event.reason()
                    )
            );

        } finally {
            MDC.clear();
        }
    }

    @KafkaListener(topics = INVENTORY_EVENTS_V1, groupId = "order-service")
    public void onInventoryFailed(InventoryReservationFailedEvent event) {
        MDC.put("eventId", event.metadata().eventId().toString());

        try {
            boolean processed = orderEventProcessor.processCancellation(
                    event.metadata().eventId(),
                    event.orderId()
            );

            if (!processed) return;

            publisher.publishOrderFailed(
                    new OrderFailedEvent(
                            next(event.metadata(), "order-service", 1),
                            event.orderId(),
                            event.reason()
                    )
            );

        } finally {
            MDC.clear();
        }
    }

    @KafkaListener(topics = INVENTORY_EVENTS_V1, groupId = "order-service")
    public void onInventoryReserved(InventoryReservedEvent event) {
        MDC.put("eventId", event.metadata().eventId().toString());

        try {
            boolean processed = orderEventProcessor.processCompletion(
                    event.metadata().eventId(),
                    event.orderId()
            );

            if (!processed) return;

            publisher.publishOrderCompleted(
                    new OrderCompletedEvent(
                            next(event.metadata(), "order-service", 1),
                            event.orderId()
                    )
            );

        } finally {
            MDC.clear();
        }
    }

}