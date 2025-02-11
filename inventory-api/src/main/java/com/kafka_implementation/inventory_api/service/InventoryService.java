package com.kafka_implementation.inventory_api.service;

import com.kafka_implementation.inventory_api.entity.Product;
import com.kafka_implementation.inventory_api.repository.ProductRepository;
import com.kafka_implementation.shared.dto.PaymentResultEvent;
import com.kafka_implementation.shared.dto.StockUpdateRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
public class InventoryService {

    private final ProductRepository productRepository;
    private final InventoryUpdateProducer inventoryUpdateProducer;

    public InventoryService(ProductRepository productRepository, InventoryUpdateProducer inventoryUpdateProducer) {
        this.productRepository = productRepository;
        this.inventoryUpdateProducer = inventoryUpdateProducer;
    }

    public Product addProduct(Product product) {
        log.info("Adding product: {}", product);
        return productRepository.save(product);
    }

    @Transactional
    public Product updateStock(StockUpdateRequest request) {
        log.info("Updating stock for productCode: {}, quantity: {}", request.getProductCode(), request.getQuantity());

        Product product = productRepository.findByProductCode(request.getProductCode())
                .orElseThrow(() -> {
                    log.warn("Product not found: {}", request.getProductCode());
                    return new IllegalArgumentException("Product not found: " + request.getProductCode());
                });

        int newStock = product.getStock() + request.getQuantity();
        boolean approved = newStock >= 0;

        if (!approved) {
            log.error("Insufficient stock for product: {}. Current stock: {}, Requested: {}",
                    request.getProductCode(), product.getStock(), request.getQuantity());
        } else {
            product.setStock(newStock);
            productRepository.save(product);
            log.info("Stock updated successfully for productCode: {}. New stock: {}", request.getProductCode(), product.getStock());
        }

        // Emit Kafka event
        PaymentResultEvent event = new PaymentResultEvent(request.getOrderId(), approved);
        inventoryUpdateProducer.sendStockUpdate(event);

        return product;
    }

    public Optional<Integer> getStock(Long productId) {
        log.info("Fetching stock for product ID: {}", productId);
        return productRepository.findById(productId).map(Product::getStock);
    }
}
