package com.kafka_implementation.order_service.consumer;

import com.kafka_implementation.order_service.producer.OrderEventPublisher;
import com.kafka_implementation.order_service.service.IdempotencyGuard;
import com.kafka_implementation.order_service.service.OrderService;
import com.kafka_implementation.shared_events.inventory.InventoryReservationFailedEvent;
import com.kafka_implementation.shared_events.inventory.InventoryReservedEvent;
import com.kafka_implementation.shared_events.order.OrderCompletedEvent;
import com.kafka_implementation.shared_events.order.OrderFailedEvent;
import com.kafka_implementation.shared_events.payment.PaymentFailedEvent;
import static com.kafka_implementation.shared_events.base.EventMetadataFactory.next;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private final OrderService orderService;
    private final OrderEventPublisher publisher;
    private final IdempotencyGuard idempotencyGuard;

    public OrderEventListener(OrderService orderService,
                              OrderEventPublisher publisher,
                              IdempotencyGuard idempotencyGuard) {
        this.orderService = orderService;
        this.publisher = publisher;
        this.idempotencyGuard = idempotencyGuard;
    }

    @KafkaListener(topics = "payment.events", groupId = "order-service")
    public void onPaymentFailed(PaymentFailedEvent event) {

        if (idempotencyGuard.alreadyProcessed(event.metadata().eventId())) return;

        orderService.cancel(event.orderId());

        publisher.publishOrderFailed(new OrderFailedEvent(
                next(event.metadata(), "order-service"),
                event.orderId(),
                event.reason()
        ));

    }

    @KafkaListener(topics = "inventory.events", groupId = "order-service")
    public void onInventoryFailed(InventoryReservationFailedEvent event) {

        if (idempotencyGuard.alreadyProcessed(event.metadata().eventId())) return;

        orderService.cancel(event.orderId());

        publisher.publishOrderFailed(new OrderFailedEvent(
                next(event.metadata(), "order-service"),
                event.orderId(),
                event.reason()
        ));
    }

    @KafkaListener(topics = "inventory.events", groupId = "order-service")
    public void onInventoryReserved(InventoryReservedEvent event) {

        if (idempotencyGuard.alreadyProcessed(event.metadata().eventId())) return;

        orderService.complete(event.orderId());

        publisher.publishOrderCompleted(new OrderCompletedEvent(
                next(event.metadata(), "order-service"),
                event.orderId()
        ));

    }
}


