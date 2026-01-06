package com.kafka_implementation.inventory_service.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.util.UUID;

@Table(name = "inventory_items")
@Data
@Entity
public class InventoryItem {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID productId;

    private int available;

    @Version
    private Long version;

    protected InventoryItem() {}

    public void reserve(int quantity) {
        if (quantity > available) {
            throw new IllegalStateException("Insufficient stock");
        }
        this.available -= quantity;
    }
}


