package com.kafka_implementation.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderPlacedEvent {

    private Long orderId;
    private String productCode;
    private int quantity;
    private Long userId;
    private double totalAmount;

}
