package com.kafka_implementation.inventory_api.service;

import com.kafka_implementation.inventory_api.entity.Inventory;
import com.kafka_implementation.inventory_api.repository.InventoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Service
public class InventoryConsumer {

    @Autowired
    private InventoryRepository inventoryRepository;

    @KafkaListener(topics = "order-topic", groupId = "inventory-service")
    public void consumeOrderEvent(String message) {
        System.out.println("Order Event Received for Inventory Check: " + message);
        // Adjust inventory or validate stock
    }

    public boolean isProductAvailable(String productCode, int quantity) {
        Optional<Inventory> inventory = inventoryRepository.findByProductCode(productCode);
        return inventory.isPresent() && inventory.get().getAvailableQuantity() >= quantity;
    }

    public void reserveProduct(String productCode, int quantity) {
        Inventory inventory = inventoryRepository.findByProductCode(productCode)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productCode));
        if (inventory.getAvailableQuantity() < quantity) {
            throw new RuntimeException("Insufficient stock for product: " + productCode);
        }
        inventory.setAvailableQuantity(inventory.getAvailableQuantity() - quantity);
        inventoryRepository.save(inventory);
    }

    public void releaseProduct(String productCode, int quantity) {
        Inventory inventory = inventoryRepository.findByProductCode(productCode)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productCode));
        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + quantity);
        inventoryRepository.save(inventory);
    }
}

