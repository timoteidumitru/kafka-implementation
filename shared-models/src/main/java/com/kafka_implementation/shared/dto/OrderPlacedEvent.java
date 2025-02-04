package com.kafka_implementation.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class OrderPlacedEvent {
    private Long orderId;
    private String productCode;
    private int quantity;
}
