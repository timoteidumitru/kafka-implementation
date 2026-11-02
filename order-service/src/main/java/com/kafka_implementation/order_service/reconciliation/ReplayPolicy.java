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

        if (evidence.originalEventCommitted()
                && eventType == FailedEventType.INVENTORY_RESERVED
                && currentStatus == OrderStatus.COMPLETED) {

            return ReplayDecision.RESOLVED_NO_REPLAY;
        }

        return ReplayDecision.REQUIRES_INVESTIGATION;
    }
}