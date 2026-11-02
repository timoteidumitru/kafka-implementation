package com.kafka_implementation.order_service.reconciliation;

public enum FailedEventType {
    PAYMENT_FAILED,
    INVENTORY_RESERVED,
    INVENTORY_RESERVATION_FAILED
}