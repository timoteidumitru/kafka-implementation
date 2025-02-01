package com.kafka_implementation.inventory_api.repository;

import com.kafka_implementation.inventory_api.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByProductCode(String productCode);

    List<Inventory> findByStockLessThan(int threshold);
}
