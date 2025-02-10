package com.kafka_implementation.inventory_api.service;

import com.kafka_implementation.shared.dto.StockUpdateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class InventoryUpdateProducer {

    private final KafkaTemplate<String, StockUpdateEvent> kafkaTemplate;

    public InventoryUpdateProducer(KafkaTemplate<String, StockUpdateEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendStockUpdate(StockUpdateEvent event) {
        log.info("Sending Stock Update Event to Kafka: {}", event);
        kafkaTemplate.send("inventory-update-topic", event);
    }
}
