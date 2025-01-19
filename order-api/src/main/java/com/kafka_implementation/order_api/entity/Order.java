package com.kafka_implementation.order_api.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String status;
    private Double totalAmount;
}

