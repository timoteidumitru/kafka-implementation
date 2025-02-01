package com.kafka_implementation.inventory_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka_implementation.inventory_api.dto.OrderEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventConsumer {

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    public OrderEventConsumer(InventoryService inventoryService, ObjectMapper objectMapper) {
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order.placed", groupId = "inventory-service")
    public void handleOrderPlaced(ConsumerRecord<String, String> record) {
        try {
            System.out.println("Received Kafka message: " + record.value());
            OrderEvent event = objectMapper.readValue(record.value(), OrderEvent.class);
            System.out.println("Parsed OrderEvent: " + event.toString());
            inventoryService.updateStock(event.getProductCode(), event.getQuantity());
        } catch (Exception e) {
            System.err.println("Failed to parse OrderEvent: " + e.getMessage());
        }
    }

}
