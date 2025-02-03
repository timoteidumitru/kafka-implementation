package com.kafka_implementation.inventory_api.controller;

import com.kafka_implementation.inventory_api.dto.RequestUpdate;
import com.kafka_implementation.inventory_api.entity.Product;
import com.kafka_implementation.inventory_api.service.InventoryService;
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
        return ResponseEntity.ok(inventoryService.addOrUpdateProduct(product));
    }

    @PutMapping("/update-stock")
    public ResponseEntity<?> updateStock(@RequestBody RequestUpdate product) {
        return ResponseEntity.ok(inventoryService.updateStock(product));
    }

    @GetMapping("/stock")
    public ResponseEntity<Optional<Integer>> getStock(@RequestParam Long id) {
        return ResponseEntity.ok(inventoryService.getStock(id));
    }
}
