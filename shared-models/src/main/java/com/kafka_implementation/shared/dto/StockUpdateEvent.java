package com.kafka_implementation.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class StockUpdateEvent {
    private Long orderId;
    private boolean approved;
}
