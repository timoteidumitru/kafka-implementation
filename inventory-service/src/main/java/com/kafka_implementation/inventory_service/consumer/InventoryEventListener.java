package com.kafka_implementation.inventory_service.consumer;

import com.kafka_implementation.inventory_service.producer.InventoryEventPublisher;
import com.kafka_implementation.inventory_service.service.IdempotencyGuard;
import com.kafka_implementation.inventory_service.service.InventoryService;
import com.kafka_implementation.shared_events.inventory.InventoryReservationFailedEvent;
import com.kafka_implementation.shared_events.inventory.InventoryReservedEvent;
import com.kafka_implementation.shared_events.payment.PaymentCompletedEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.kafka_implementation.shared_events.base.EventMetadataFactory.next;

@Component
public class InventoryEventListener {

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

    @CircuitBreaker(name = "inventory-kafka", fallbackMethod = "onFailure")
    @Retry(name = "inventory-kafka")
    @KafkaListener(topics = "payment.events", groupId = "inventory-service")
    public void onPaymentCompleted(PaymentCompletedEvent event) {

        if (idempotencyGuard.alreadyProcessed(event.metadata().eventId())) return;

        try {
            inventoryService.reserveStock(event.productId(), event.quantity());

            publisher.publishReserved(new InventoryReservedEvent(
                    next(event.metadata(), "inventory-service"),
                    event.orderId(),
                    event.productId(),
                    event.quantity()
            ));

        } catch (Exception ex) {
            publisher.publishFailed(new InventoryReservationFailedEvent(
                    next(event.metadata(), "inventory-service"),
                    event.orderId(),
                    ex.getMessage()
            ));
        }
    }

    private void onFailure(PaymentCompletedEvent event, Throwable ex) {
        publisher.publishFailed(new InventoryReservationFailedEvent(
                next(event.metadata(), "inventory-service"),
                event.orderId(),
                "Inventory service unavailable"
        ));
    }
}




