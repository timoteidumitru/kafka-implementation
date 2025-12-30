package com.kafka_implementation.inventory_service.producer;

import com.kafka_implementation.inventory_service.config.KafkaTopicsConfig;
import com.kafka_implementation.shared_events.inventory.InventoryReservedEvent;
import com.kafka_implementation.shared_events.inventory.InventoryReservationFailedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public InventoryEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishReserved(InventoryReservedEvent event) {
        kafkaTemplate.send(
                KafkaTopicsConfig.INVENTORY_EVENTS,
                event.orderId().toString(),
                event
        );
    }

    public void publishReservationFailed(InventoryReservationFailedEvent event) {
        kafkaTemplate.send(
                KafkaTopicsConfig.INVENTORY_EVENTS,
                event.orderId().toString(),
                event
        );
    }
}
