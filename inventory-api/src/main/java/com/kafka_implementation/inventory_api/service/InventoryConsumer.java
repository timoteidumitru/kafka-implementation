package com.kafka_implementation.inventory_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka_implementation.inventory_api.entity.Product;
import com.kafka_implementation.inventory_api.repository.ProductRepository;
import com.kafka_implementation.shared.dto.OrderPlacedEvent;
import com.kafka_implementation.shared.dto.StockUpdateEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class InventoryConsumer {

    private final ProductRepository productRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public InventoryConsumer(ProductRepository productRepository, KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order.placed", groupId = "inventory-service")
    public void handleOrderPlaced(ConsumerRecord<String, String> record) {
        try {
            OrderPlacedEvent event = objectMapper.readValue(record.value(), OrderPlacedEvent.class);

            Optional<Product> productOpt = productRepository.findByProductCode(event.getProductCode());

            boolean approved = productOpt.isPresent() && productOpt.get().getStock() >= event.getQuantity();

            if (approved) {
                Product product = productOpt.get();
                product.setStock(product.getStock() - event.getQuantity());
                productRepository.save(product);
            }

            StockUpdateEvent stockUpdateEvent = new StockUpdateEvent(event.getOrderId(), approved);
            kafkaTemplate.send("inventory.stock.update", objectMapper.writeValueAsString(stockUpdateEvent));
        } catch (Exception e) {
            System.err.println("❌ Failed to process order: " + e.getMessage());
        }
    }
}
