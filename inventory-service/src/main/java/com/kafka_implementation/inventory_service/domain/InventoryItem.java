package com.kafka_implementation.inventory_service.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.util.UUID;

@Entity
@Table(name = "inventory_items")
@Data
public class InventoryItem {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID productId;

    private int availableQuantity;

    protected InventoryItem() {}


    public void reserve(int quantity) {
        if (availableQuantity < quantity) {
            throw new IllegalStateException("Insufficient stock");
        }
        this.availableQuantity -= quantity;
    }
}

