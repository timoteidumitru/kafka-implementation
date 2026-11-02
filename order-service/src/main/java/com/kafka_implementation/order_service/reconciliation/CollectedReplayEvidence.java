package com.kafka_implementation.order_service.reconciliation;

import com.kafka_implementation.order_service.domain.OrderStatus;

import java.util.Optional;

public record CollectedReplayEvidence(
        boolean originalEventCommitted,
        Optional<OrderStatus> orderStatus
) {}