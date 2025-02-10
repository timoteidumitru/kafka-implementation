package com.kafka_implementation.inventory_api.controller;

import com.kafka_implementation.inventory_api.entity.Product;
import com.kafka_implementation.inventory_api.service.InventoryService;
import com.kafka_implementation.shared.dto.StockUpdateRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/add-product")
    public ResponseEntity<Product> addProduct(@RequestBody Product product) {
        return ResponseEntity.ok(inventoryService.addProduct(product));
    }

    @PutMapping("/update-stock")
    public ResponseEntity<?> updateStock(@RequestBody StockUpdateRequest request) {
        return ResponseEntity.ok(inventoryService.updateStock(request));
    }

    @GetMapping("/stock")
    public ResponseEntity<Optional<Integer>> getStock(@RequestParam Long productId) {
        return ResponseEntity.ok(inventoryService.getStock(productId));
    }
}
