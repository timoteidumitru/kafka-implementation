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

    @Column(nullable = false)
    private String status = "PENDING";

    private Long userId = 772L;

    @Column(nullable = false)
    private String productCode;

    @Column(nullable = false)
    private int quantity;

    public Order(Long orderId, String status) {
        this.id = orderId;
        this.status = status;
    }
}
