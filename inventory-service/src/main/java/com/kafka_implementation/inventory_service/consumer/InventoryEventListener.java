package com.kafka_implementation.inventory_service.consumer;

import com.kafka_implementation.inventory_service.producer.InventoryEventPublisher;
import com.kafka_implementation.inventory_service.service.IdempotencyGuard;
import com.kafka_implementation.inventory_service.service.InventoryService;
import com.kafka_implementation.shared_events.base.EventMetadata;
import com.kafka_implementation.shared_events.inventory.InventoryReservationFailedEvent;
import com.kafka_implementation.shared_events.inventory.InventoryReserveRequestedEvent;
import com.kafka_implementation.shared_events.inventory.InventoryReservedEvent;
import com.kafka_implementation.shared_events.payment.PaymentCompletedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class InventoryEventListener {

    private final InventoryService inventoryService;
    private final InventoryEventPublisher publisher;
    private final IdempotencyGuard idempotencyGuard;

    public InventoryEventListener(InventoryService inventoryService,
                                  InventoryEventPublisher publisher,
                                  IdempotencyGuard idempotencyGuard) {
        this.inventoryService = inventoryService;
        this.publisher = publisher;
        this.idempotencyGuard = idempotencyGuard;
    }

    @KafkaListener(topics = "payment.events", groupId = "inventory-service")
    public void onPaymentCompleted(PaymentCompletedEvent event) {

        if (idempotencyGuard.alreadyProcessed(event.metadata().eventId())) return;

        try {
            // 1. Reserve stock for order
            inventoryService.reserveStock(event.productId(), event.quantity());

            // 2. Publish success event
            publisher.publishReserved(new InventoryReservedEvent(
                    nextMetadata(event.metadata()),
                    event.orderId(),
                    event.productId(),
                    event.quantity()
            ));

        } catch (Exception ex) {
            publisher.publishReservationFailed(new InventoryReservationFailedEvent(
                    nextMetadata(event.metadata()),
                    event.orderId(),
                    ex.getMessage()
            ));
        }
    }

    private EventMetadata nextMetadata(EventMetadata previous) {
        return new EventMetadata(
                UUID.randomUUID(),
                previous.correlationId(),
                Instant.now(),
                "inventory-service",
                previous.version()
        );
    }
}



