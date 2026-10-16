package com.kafka_implementation.order_service.outbox.service;

import com.kafka_implementation.order_service.outbox.domain.OutboxEvent;
import com.kafka_implementation.order_service.outbox.domain.OutboxStatus;
import com.kafka_implementation.order_service.outbox.repository.OutboxEventRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OutboxPublicationStateService {

    private final OutboxEventRepository repository;

    public OutboxPublicationStateService(
            OutboxEventRepository repository
    ) {
        this.repository = repository;
    }

    @Transactional
    public void recordFailedAttempt(UUID outboxEventId) {

        OutboxEvent event = repository.findById(outboxEventId)
                .orElseThrow(() -> new IllegalStateException(
                        "Outbox event not found: " + outboxEventId
                ));

        // Never record a failed publication attempt
        // against an already published event.
        if (event.getStatus() == OutboxStatus.PUBLISHED) {
            return;
        }

        event.recordAttempt();

        // Hibernate dirty checking persists the increment
        // when the transaction commits.
    }

    @Transactional
    public void markPublished(UUID outboxEventId) {

        OutboxEvent event = repository.findById(outboxEventId)
                .orElseThrow(() -> new IllegalStateException(
                        "Outbox event not found: " + outboxEventId
                ));

        // Idempotency: do not overwrite the original publication timestamp.
        if (event.getStatus() == OutboxStatus.PUBLISHED) {
            return;
        }

        event.markPublished();

        // No repository.save() is required:
        // Hibernate dirty checking persists the state at commit.
    }
}
