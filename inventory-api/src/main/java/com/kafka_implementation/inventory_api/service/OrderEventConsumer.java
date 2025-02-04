package com.kafka_implementation.inventory_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka_implementation.inventory_api.dto.OrderEvent;
import com.kafka_implementation.inventory_api.dto.RequestUpdate;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class OrderEventConsumer {

    @Autowired
    private final InventoryService inventoryService;
    @Autowired
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
            RequestUpdate updateProduct = new RequestUpdate();
            updateProduct.setProductCode(event.getProductCode());
            updateProduct.setQuantity(event.getQuantity());
            System.out.println("Parsed OrderEvent: " + event.toString());
            inventoryService.updateStock(updateProduct);
        } catch (Exception e) {
            System.err.println("Failed to parse OrderEvent: " + e.getMessage());
        }
    }

}
