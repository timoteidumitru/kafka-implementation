package com.kafka_implementation.inventory_api.controller;

import com.kafka_implementation.inventory_api.entity.Product;
import com.kafka_implementation.inventory_api.service.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    @Autowired
    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/add-product")
    public ResponseEntity<Product> addProduct(@RequestBody Product product) {
        try {
            // Call the service to add the product to the inventory
            Product addedProduct = inventoryService.addProduct(product);

            if (addedProduct == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(addedProduct); // Return the created product with status 201
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PutMapping("/update-stock")
    public ResponseEntity<?> updateStock(
            @RequestParam String productCode,
            @RequestParam Double quantity) {

        try {
            Product updatedProduct = inventoryService.updateStock(productCode, quantity);

            if (updatedProduct == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(updatedProduct);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred: " + ex.getMessage());
        }
    }

    @GetMapping()
    public ResponseEntity<Optional<Double>> getStock(@RequestParam Long id) {
        return ResponseEntity.ok(inventoryService.getStock(id));
    }

}
