package com.kafka_implementation.inventory_api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class RequestUpdate {

    private String productCode;
    private Integer quantity;

}
