package com.kafka_implementation.order_service.outbox.domain;

public enum OutboxStatus {
    PENDING,
    IN_PROGRESS,
    PUBLISHED
}
