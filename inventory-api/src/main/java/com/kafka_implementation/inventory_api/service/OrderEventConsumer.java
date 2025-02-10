package com.kafka_implementation.inventory_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka_implementation.shared.dto.OrderPlacedEvent;
import com.kafka_implementation.shared.dto.StockUpdateRequest;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class OrderEventConsumer {

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    public OrderEventConsumer(InventoryService inventoryService, ObjectMapper objectMapper) {
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order.topic", groupId = "inventory-service")
    public void handleOrderPlaced(ConsumerRecord<String, String> record) {
        try {
            OrderPlacedEvent event = objectMapper.readValue(record.value(), OrderPlacedEvent.class);
            StockUpdateRequest updateRequest = new StockUpdateRequest();
            updateRequest.setProductCode(event.getProductCode());
            updateRequest.setQuantity(event.getQuantity());
            updateRequest.setOrderId(event.getOrderId());

            inventoryService.updateStock(updateRequest);
        } catch (Exception e) {
            System.err.println("Failed to parse OrderEvent: " + e.getMessage());
        }
    }
}
