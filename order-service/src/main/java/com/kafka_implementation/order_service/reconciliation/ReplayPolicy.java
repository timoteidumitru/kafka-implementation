package com.kafka_implementation.order_service.reconciliation;

import com.kafka_implementation.order_service.domain.OrderStatus;

import java.util.Objects;

public final class ReplayPolicy {

    public ReplayDecision evaluate(
            FailedEventType eventType,
            OrderStatus currentStatus
    ) {
        return evaluate(
                eventType,
                currentStatus,
                new ReplayEvidence(false)
        );
    }

    public ReplayDecision evaluate(
            FailedEventType eventType,
            OrderStatus currentStatus,
            ReplayEvidence evidence
    ) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(currentStatus, "currentStatus");
        Objects.requireNonNull(evidence, "evidence");

        // Current evidence is insufficient to establish
        // that a failed event is safely resolved.
        //
        // Do not automatically authorise replay or
        // declare reconciliation complete.

        return ReplayDecision.REQUIRES_INVESTIGATION;
    }
}
