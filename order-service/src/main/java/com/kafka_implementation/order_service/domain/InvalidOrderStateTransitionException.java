package com.kafka_implementation.order_service.domain;

public class InvalidOrderStateTransitionException
        extends IllegalStateException {

    public InvalidOrderStateTransitionException(String message) {
        super(message);
    }
}