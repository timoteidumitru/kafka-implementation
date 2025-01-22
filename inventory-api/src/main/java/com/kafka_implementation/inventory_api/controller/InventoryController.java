package com.kafka_implementation.inventory_api.controller;

import com.kafka_implementation.inventory_api.service.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    @Autowired
    private InventoryService inventoryService;

    @GetMapping("/check")
    public ResponseEntity<String> checkAvailability(
            @RequestParam String productCode,
            @RequestParam int quantity) {
        boolean isAvailable = inventoryService.isProductAvailable(productCode, quantity);
        if (isAvailable) {
            return ResponseEntity.ok("Product is available.");
        } else {
            return ResponseEntity.status(404).body("Product is not available.");
        }
    }

    @PostMapping("/reserve")
    public ResponseEntity<String> reserveProduct(
            @RequestParam String productCode,
            @RequestParam int quantity) {
        try {
            inventoryService.reserveProduct(productCode, quantity);
            return ResponseEntity.ok("Product reserved successfully.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }

    @PostMapping("/release")
    public ResponseEntity<String> releaseProduct(
            @RequestParam String productCode,
            @RequestParam int quantity) {
        try {
            inventoryService.releaseProduct(productCode, quantity);
            return ResponseEntity.ok("Product released successfully.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }
}
