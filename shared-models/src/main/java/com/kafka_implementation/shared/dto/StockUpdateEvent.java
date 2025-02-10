package com.kafka_implementation.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockUpdateEvent {

    private Long orderId;
    private boolean approved;

}
