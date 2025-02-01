package com.kafka_implementation.inventory_api.repository;

import com.kafka_implementation.inventory_api.entity.Inventory;
import com.kafka_implementation.inventory_api.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Product findByProductCode(String productCode);

}
