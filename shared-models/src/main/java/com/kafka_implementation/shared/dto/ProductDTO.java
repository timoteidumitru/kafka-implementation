package com.kafka_implementation.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductDTO {

    private String productCode;
    private String name;
    private String description;
    private Double price;
    private String category;
    private Integer stock;

}

