package com.kafka_implementation.inventory_api.service;

import com.kafka_implementation.inventory_api.entity.Product;
import com.kafka_implementation.inventory_api.repository.InventoryRepository;
import com.kafka_implementation.inventory_api.repository.ProductRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class InventoryService {
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;

    public InventoryService(InventoryRepository inventoryRepository, ProductRepository productRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
    }

    public Product addProduct(Product product) {
        Product existingProduct = productRepository.findByProductCode(product.getProductCode());
        if (existingProduct != null) {
            // If the product already exists, return null or throw an exception
            return null; // Or throw a custom exception
        }

        return productRepository.save(product);
    }

    public Product updateStock(String productCode, Double quantity) {
        Product product = productRepository.findByProductCode(productCode.trim());
        if (product == null) {
            System.err.println("Product not found in inventory for productCode: " + productCode);
            return null; // or throw an exception if the product is not found
        }

        // Update stock
        product.setStock(product.getStock() + quantity.intValue()); // Correctly update the stock
        productRepository.save(product);  // Save the updated product back to the database
        return product;  // Return the updated product
    }

    public Optional<Double> getStock(Long id) {
        Optional<Product> product = productRepository.findById(id);
        if (product.isPresent()) {
            return product.map(Product::getStock);
        }
        return Optional.of(0.0);
    }

}

