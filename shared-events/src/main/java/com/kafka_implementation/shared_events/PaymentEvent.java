package com.kafka_implementation.shared_events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentEvent {

    private Long orderId;
    private String productCode;
    private int quantity;
    private boolean approved;

}
