package com.kafka_implementation.inventory_service.producer;

import com.kafka_implementation.shared_events.base.DomainEvent;
import com.kafka_implementation.shared_events.inventory.InventoryReservationFailedEvent;
import com.kafka_implementation.shared_events.inventory.InventoryReservedEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class InventoryEventPublisher {

    private final KafkaTemplate<String, DomainEvent> kafkaTemplate;

    @Value("${app.topics.inventory-events}")
    private String inventoryEventsTopic;

    public InventoryEventPublisher(@Qualifier("inventoryKafkaTemplate") KafkaTemplate<String, DomainEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @CircuitBreaker(name = "inventory-producer", fallbackMethod = "fallback")
    public void publishReserved(InventoryReservedEvent event) {
        kafkaTemplate.send(inventoryEventsTopic, event.getAggregateId().toString(), event);
    }

    @CircuitBreaker(name = "inventory-producer", fallbackMethod = "fallback")
    public void publishFailed(InventoryReservationFailedEvent event) {
        kafkaTemplate.send(inventoryEventsTopic, event.getAggregateId().toString(), event);
    }

    private void fallback(DomainEvent event, Throwable ex) {
        log.error("[InventoryEventPublisher] Failed to publish event {}", event.getEventType(), ex);
    }
}