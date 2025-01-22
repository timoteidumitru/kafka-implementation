package com.kafka_implementation.inventory_api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class InventoryProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${inventory.topic.name:inventory-update-topic}")
    private String inventoryTopic;

    public InventoryProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendStockUpdate(String message) {
        kafkaTemplate.send(inventoryTopic, message);
        System.out.println("Stock Update Sent: " + message);
    }
}

