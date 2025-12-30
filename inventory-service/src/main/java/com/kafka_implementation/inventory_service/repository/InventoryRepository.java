package com.kafka_implementation.inventory_service.repository;

import com.kafka_implementation.inventory_service.domain.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<InventoryItem, UUID> {

    Optional<InventoryItem> findByProductId(UUID productId);
}
