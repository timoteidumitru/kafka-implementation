package com.kafka_implementation.order_service.reconciliation;

import com.kafka_implementation.order_service.domain.OrderStatus;
import com.kafka_implementation.order_service.repository.OrderRepository;
import com.kafka_implementation.order_service.repository.ProcessedEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class ReplayEvidenceCollector {

    private final ProcessedEventRepository processedEventRepository;
    private final OrderRepository orderRepository;

    public ReplayEvidenceCollector(
            ProcessedEventRepository processedEventRepository,
            OrderRepository orderRepository
    ) {
        this.processedEventRepository = processedEventRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public CollectedReplayEvidence collect(
            UUID eventId,
            UUID orderId
    ) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(orderId, "orderId");

        boolean committed =
                processedEventRepository.existsById(eventId);

        Optional<OrderStatus> status =
                orderRepository.findById(orderId)
                        .map(order -> order.getStatus());

        return new CollectedReplayEvidence(committed, status);
    }
}