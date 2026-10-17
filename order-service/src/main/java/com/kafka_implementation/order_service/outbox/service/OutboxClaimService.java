package com.kafka_implementation.order_service.outbox.service;

import com.kafka_implementation.order_service.outbox.domain.OutboxEvent;
import com.kafka_implementation.order_service.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class OutboxClaimService {

    private final OutboxEventRepository repository;

    public OutboxClaimService(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public List<OutboxEvent> claimPendingBatch(
            UUID workerId,
            Instant leaseExpiresAt,
            int batchSize
    ) {
        // Validate before acquiring database locks.
        Objects.requireNonNull(workerId, "workerId must not be null");
        Objects.requireNonNull(
                leaseExpiresAt,
                "leaseExpiresAt must not be null"
        );

        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "batchSize must be greater than zero"
            );
        }

        // SELECT ... FOR UPDATE SKIP LOCKED
        List<OutboxEvent> events =
                repository.lockPendingBatch(batchSize);

        // These entities are managed by JPA inside this transaction.
        for (OutboxEvent event : events) {
            event.claim(workerId, leaseExpiresAt);
        }

        // Hibernate writes the changes when the transaction commits.
        return events;
    }

}
