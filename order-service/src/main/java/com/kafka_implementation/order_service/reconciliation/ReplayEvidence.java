package com.kafka_implementation.order_service.reconciliation;

public record ReplayEvidence(
        boolean originalEventCommitted
) {
}