package com.kafka_implementation.order_service.reconciliation;

public enum ProcessingOutcome {

    TRANSITION_APPLIED,

    ALREADY_IN_TARGET_STATE
}
