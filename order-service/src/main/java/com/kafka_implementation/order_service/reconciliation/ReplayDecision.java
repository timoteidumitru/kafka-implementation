package com.kafka_implementation.order_service.reconciliation;

public enum ReplayDecision {
    REQUIRES_INVESTIGATION,
    RESOLVED_NO_REPLAY,
    APPROVED_FOR_REPLAY
}