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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

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

        MDC.put("eventId", event.metadata().eventId().toString());
        MDC.put("correlationId", event.metadata().correlationId().toString());
        MDC.put("orderId", event.orderId().toString());

        try {
            if (idempotencyGuard.alreadyProcessed(event.metadata().eventId())) {
                log.info("Duplicate PaymentFailedEvent ignored");
                return;
            }

            log.warn("Handling PaymentFailedEvent: {}", event.reason());

            orderService.cancel(event.orderId());

            publisher.publishOrderFailed(
                    new OrderFailedEvent(
                            next(event.metadata(), "order-service"),
                            event.orderId(),
                            event.reason()
                    )
            );

            log.info("Order marked as FAILED");

        } finally {
            MDC.clear();
        }
    }

    @Retry(name = "order-kafka")
    @CircuitBreaker(name = "order-kafka", fallbackMethod = "fallback")
    @Bulkhead(name = "order-kafka")
    @KafkaListener(topics = "inventory.events", groupId = "order-service")
    public void onInventoryFailed(InventoryReservationFailedEvent event) {

        MDC.put("eventId", event.metadata().eventId().toString());
        MDC.put("correlationId", event.metadata().correlationId().toString());
        MDC.put("orderId", event.orderId().toString());


        try {
            if (idempotencyGuard.alreadyProcessed(event.metadata().eventId())) {
                log.info("Duplicate InventoryReservationFailedEvent ignored");
                return;
            }

            log.warn("Inventory reservation failed: {}", event.reason());

            orderService.cancel(event.orderId());

            publisher.publishOrderFailed(
                    new OrderFailedEvent(
                            next(event.metadata(), "order-service"),
                            event.orderId(),
                            event.reason()
                    )
            );

            log.info("Order marked as FAILED");

        } finally {
            MDC.clear();
        }
    }

    @Retry(name = "order-kafka")
    @CircuitBreaker(name = "order-kafka", fallbackMethod = "fallback")
    @Bulkhead(name = "order-kafka")
    @KafkaListener(topics = "inventory.events", groupId = "order-service")
    public void onInventoryReserved(InventoryReservedEvent event) {

        MDC.put("eventId", event.metadata().eventId().toString());
        MDC.put("correlationId", event.metadata().correlationId().toString());
        MDC.put("orderId", event.orderId().toString());

        try {
            if (idempotencyGuard.alreadyProcessed(event.metadata().eventId())) {
                log.info("Duplicate InventoryReservedEvent ignored");
                return;
            }

            log.info("Completing order");

            orderService.complete(event.orderId());

            publisher.publishOrderCompleted(
                    new OrderCompletedEvent(
                            next(event.metadata(), "order-service"),
                            event.orderId()
                    )
            );

            log.info("Order COMPLETED successfully");

        } finally {
            MDC.clear();
        }
    }

    private void fallback(Object event, Throwable ex) {
        log.error(
                "Kafka consumer rejected event due to resilience policy",
                ex
        );
    }
}
