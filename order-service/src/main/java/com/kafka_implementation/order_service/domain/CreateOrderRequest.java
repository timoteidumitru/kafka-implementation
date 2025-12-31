package com.kafka_implementation.order_service.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderRequest(
        UUID userId,
        UUID productId,
        int quantity,
        BigDecimal price
) {}

