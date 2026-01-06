package com.kafka_implementation.inventory_service.service;

import com.kafka_implementation.inventory_service.domain.InventoryItem;
import com.kafka_implementation.inventory_service.repository.InventoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class InventoryService {

    private final InventoryRepository repository;

    public InventoryService(InventoryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void reserveStock(UUID productId, int quantity) {

        if (quantity <= 0) {
            throw new IllegalArgumentException("Invalid quantity");
        }

        InventoryItem item = repository.findByProductId(productId)
                .orElseThrow(() -> new IllegalStateException("Product not found"));

        item.reserve(quantity); // domain logic (throws if insufficient stock)
        repository.save(item);
    }
}


