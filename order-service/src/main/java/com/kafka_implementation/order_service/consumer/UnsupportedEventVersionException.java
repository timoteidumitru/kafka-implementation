package com.kafka_implementation.order_service.consumer;

public class UnsupportedEventVersionException
        extends RuntimeException {

    public UnsupportedEventVersionException(String message) {
        super(message);
    }
}
