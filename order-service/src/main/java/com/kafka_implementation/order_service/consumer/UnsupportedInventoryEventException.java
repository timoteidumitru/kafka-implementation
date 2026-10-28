package com.kafka_implementation.order_service.consumer;

public class UnsupportedInventoryEventException
        extends RuntimeException {

    public UnsupportedInventoryEventException(String message) {
        super(message);
    }
}
