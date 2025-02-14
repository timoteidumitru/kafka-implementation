package com.kafka_implementation.inventory_api.service;

import com.kafka_implementation.inventory_api.entity.Product;
import com.kafka_implementation.inventory_api.repository.ProductRepository;
import com.kafka_implementation.shared.dto.PaymentResultEvent;
import com.kafka_implementation.shared.dto.StockUpdateRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    public void addProduct(Product product) {
        log.info("Adding product: {}", product);
        productRepository.save(product);
    }

    @Transactional
    public void updateStock(StockUpdateRequest request) {
        log.info("Updating stock for productCode: {}, quantity: {}", request.getProductCode(), request.getQuantity());

        Product product = productRepository.findById(request.getOrderId())
                .orElseThrow(() -> {
                    log.warn("Product not found: {}", request.getOrderId());
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

    }

    public Optional<Integer> getStock(Long productId) {
        log.info("Fetching stock for product ID: {}", productId);
        return productRepository.findById(productId).map(Product::getStock);
    }

    public List<Product> getAllProducts(){
        log.info("Fetching all products from inventory.");
        return productRepository.findAll();
    }
}
