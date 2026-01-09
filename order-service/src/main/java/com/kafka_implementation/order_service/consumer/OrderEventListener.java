package com.kafka_implementation.order_service.consumer;

import com.kafka_implementation.order_service.producer.OrderEventPublisher;
import com.kafka_implementation.order_service.service.IdempotencyGuard;
import com.kafka_implementation.order_service.service.OrderService;
import com.kafka_implementation.shared_events.inventory.InventoryReservationFailedEvent;
import com.kafka_implementation.shared_events.inventory.InventoryReservedEvent;
import com.kafka_implementation.shared_events.order.OrderCompletedEvent;
import com.kafka_implementation.shared_events.order.OrderFailedEvent;
import com.kafka_implementation.shared_events.payment.PaymentFailedEvent;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import static com.kafka_implementation.shared_events.base.EventMetadataFactory.next;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private final OrderService orderService;
    private final OrderEventPublisher publisher;
    private final IdempotencyGuard idempotencyGuard;

    public OrderEventListener(
            OrderService orderService,
            OrderEventPublisher publisher,
            IdempotencyGuard idempotencyGuard) {
        this.orderService = orderService;
        this.publisher = publisher;
        this.idempotencyGuard = idempotencyGuard;
    }

    @Retry(name = "order-kafka")
    @CircuitBreaker(name = "order-kafka", fallbackMethod = "fallback")
    @Bulkhead(name = "order-kafka")
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

    @Retry(name = "order-kafka")
    @CircuitBreaker(name = "order-kafka", fallbackMethod = "fallback")
    @Bulkhead(name = "order-kafka")
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

    @Retry(name = "order-kafka")
    @CircuitBreaker(name = "order-kafka", fallbackMethod = "fallback")
    @Bulkhead(name = "order-kafka")
    @KafkaListener(topics = "inventory.events", groupId = "order-service")
    public void onInventoryReserved(InventoryReservedEvent event) {

        if (idempotencyGuard.alreadyProcessed(event.metadata().eventId())) return;

        orderService.complete(event.orderId());

        publisher.publishOrderCompleted(new OrderCompletedEvent(
                next(event.metadata(), "order-service"),
                event.orderId()
        ));
    }

    private void fallback(Object event, Throwable ex) {
        System.err.println(
                "[ORDER-SERVICE] Kafka consumer failure. Event rejected by resilience layer. Cause: "
                        + ex.getMessage()
        );
    }
}
