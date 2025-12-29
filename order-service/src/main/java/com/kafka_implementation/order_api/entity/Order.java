package com.kafka_implementation.order_api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Table(name = "`order`")
@AllArgsConstructor
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;
    private String productCode;
    private int quantity;
    private String status = "PENDING";

    public Order(Long orderId, String productCode, int quantity, String status) {
        this.orderId = orderId;
        this.productCode = productCode;
        this.quantity = quantity;
        this.status = status;
    }
}
