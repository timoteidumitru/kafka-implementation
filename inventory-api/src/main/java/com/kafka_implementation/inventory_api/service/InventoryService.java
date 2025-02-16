package com.kafka_implementation.inventory_api.service;

import com.kafka_implementation.inventory_api.entity.Product;
import com.kafka_implementation.inventory_api.repository.ProductRepository;
import com.kafka_implementation.shared.dto.OrderEvent;
import com.kafka_implementation.shared.dto.PaymentEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class InventoryService {

    private final ProductRepository productRepository;

    public InventoryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public void addProduct(Product product) {
        log.info("Adding product: {}", product);
        productRepository.save(product);
        log.info("Product added successfully: {}", product);
    }

    @Transactional
    public void updateStock(OrderEvent request) {
        log.info("Updating stock for productCode: {}, quantity: {}, operation: {}",
                request.getProductCode(), request.getQuantity(), request.getOperation());

        Product product = (Product) productRepository.findByProductCode(request.getProductCode())
                .orElseThrow(() -> {
                    log.warn("Product not found: {}", request.getProductCode());
                    return new IllegalArgumentException("Product not found: " + request.getProductCode());
                });

        int newStock = 0;
        boolean approved = false;

        if ("restock".equals(request.getOperation())) {
            newStock = product.getStock() + request.getQuantity();
            approved = true;
        } else if ("buy".equals(request.getOperation())) {
            newStock = product.getStock() - request.getQuantity();
            approved = newStock >= 0;
        }

        if (!approved) {
            log.error("Insufficient stock for product: {}. Current stock: {}, Requested: {}",
                    request.getProductCode(), product.getStock(), request.getQuantity());
        } else {
            product.setStock(newStock);
            productRepository.save(product);
            log.info("Stock updated successfully for productCode: {}. New stock: {}",
                    request.getProductCode(), product.getStock());
        }
    }

    public Optional<Integer> getStock(Long productId) {
        log.info("Fetching stock for product ID: {}", productId);
        return productRepository.findById(productId).map(Product::getStock);
    }

    public List<Product> getAllProducts(){
        log.info("Fetching all products from inventory.");
        List<Product> products = productRepository.findAll();
        log.info("Fetched {} products from inventory.", products.size());
        return products;
    }
}
