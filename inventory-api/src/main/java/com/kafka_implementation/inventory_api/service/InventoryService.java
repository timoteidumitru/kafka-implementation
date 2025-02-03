package com.kafka_implementation.inventory_api.service;

import com.kafka_implementation.inventory_api.dto.RequestUpdate;
import com.kafka_implementation.inventory_api.entity.Product;
import com.kafka_implementation.inventory_api.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
public class InventoryService {

    private final ProductRepository productRepository;

    public InventoryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product addOrUpdateProduct(Product product) {
        log.info("Request to add/update product: {}", product);

        Product savedProduct = productRepository.save(product);
        log.info("Product successfully saved with ID: {}", savedProduct.getId());

        return savedProduct;
    }

    @Transactional
    public Product updateStock(RequestUpdate productCode) {
        log.info("Request to update stock for productCode: {}, quantity: {}", productCode.getProductCode(), productCode.getQuantity());

        Product product = productRepository.findByProductCode(productCode.getProductCode())
                .orElseThrow(() -> {
                    log.warn("Product not found: {}", productCode);
                    return new IllegalArgumentException("Product not found: " + productCode);
                });

        int newStock = product.getStock() + productCode.getQuantity();
        if (newStock < 0) {
            log.error("Insufficient stock for product: {}. Current stock: {}, Requested: {}", productCode, product.getStock(), productCode.getQuantity());
            throw new IllegalArgumentException("Insufficient stock for product: " + productCode);
        }

        product.setStock(newStock);
        Product updatedProduct = productRepository.save(product);

        log.info("Stock successfully updated for productCode: {}. New stock: {}", productCode, updatedProduct.getStock());
        return updatedProduct;
    }

    public Optional<Integer> getStock(Long id) {
        log.info("Request to get stock for product ID: {}", id);

        Optional<Integer> stock = productRepository.findById(id).map(Product::getStock);

        if (stock.isPresent()) {
            log.info("Stock retrieved for product ID: {}. Available stock: {}", id, stock.get());
        } else {
            log.warn("Product ID: {} not found. Returning stock as 0.0", id);
        }

        return stock;
    }
}
