package com.kafka_implementation.inventory_service.consumer;

import com.kafka_implementation.inventory_service.producer.InventoryEventPublisher;
import com.kafka_implementation.inventory_service.service.IdempotencyGuard;
import com.kafka_implementation.inventory_service.service.InventoryService;
import com.kafka_implementation.shared_events.inventory.InventoryReservationFailedEvent;
import com.kafka_implementation.shared_events.inventory.InventoryReservedEvent;
import com.kafka_implementation.shared_events.payment.PaymentCompletedEvent;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.kafka_implementation.shared_events.base.EventMetadataFactory.next;

@Component
public class InventoryEventListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventListener.class);

    private final InventoryService inventoryService;
    private final InventoryEventPublisher publisher;
    private final IdempotencyGuard idempotencyGuard;

    public InventoryEventListener(
            InventoryService inventoryService,
            InventoryEventPublisher publisher,
            IdempotencyGuard idempotencyGuard) {
        this.inventoryService = inventoryService;
        this.publisher = publisher;
        this.idempotencyGuard = idempotencyGuard;
    }

    @KafkaListener(topics = "payment.events", groupId = "inventory-service")
    @Retry(name = "inventory-kafka")
    @CircuitBreaker(name = "inventory-kafka", fallbackMethod = "fallback")
    @Bulkhead(
            name = "inventory-kafka",
            type = Bulkhead.Type.THREADPOOL
    )
    public void onPaymentCompleted(PaymentCompletedEvent event) {

        try {
            // ===== MDC context =====
            MDC.put("eventId", event.metadata().eventId().toString());
            MDC.put("correlationId", event.metadata().correlationId().toString());
            MDC.put("orderId", event.orderId().toString());
            MDC.put("productId", event.productId().toString());

            if (idempotencyGuard.alreadyProcessed(event.metadata().eventId())) {
                log.info("Duplicate payment event detected — skipping processing");
                return;
            }

            log.info(
                    "Reserving inventory for orderId={}, productId={}, quantity={}",
                    event.orderId(),
                    event.productId(),
                    event.quantity()
            );

            inventoryService.reserveStock(
                    event.productId(),
                    event.quantity()
            );

            publisher.publishReserved(
                    new InventoryReservedEvent(
                            next(event.metadata(), "inventory-service"),
                            event.orderId(),
                            event.productId(),
                            event.quantity()
                    )
            );

            log.info("Inventory successfully reserved");

        } finally {
            MDC.clear();
        }
    }

    /**
     * CircuitBreaker / Retry / Bulkhead fallback
     */
    private void fallback(PaymentCompletedEvent event, Throwable ex) {

        try {
            MDC.put("eventId", event.metadata().eventId().toString());
            MDC.put("correlationId", event.metadata().correlationId().toString());
            MDC.put("orderId", event.orderId().toString());
            MDC.put("productId", event.productId().toString());

            log.error(
                    "Inventory reservation failed or throttled",
                    ex
            );

            publisher.publishFailed(
                    new InventoryReservationFailedEvent(
                            next(event.metadata(), "inventory-service"),
                            event.orderId(),
                            "Inventory service unavailable"
                    )
            );

        } finally {
            MDC.clear();
        }
    }
}
