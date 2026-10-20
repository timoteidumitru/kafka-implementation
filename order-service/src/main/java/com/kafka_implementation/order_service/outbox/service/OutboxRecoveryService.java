package com.kafka_implementation.order_service.outbox.service;

import com.kafka_implementation.order_service.outbox.domain.OutboxEvent;
import com.kafka_implementation.order_service.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class OutboxRecoveryService {

    private final OutboxEventRepository repository;

    public OutboxRecoveryService(
            OutboxEventRepository repository
    ) {
        this.repository = repository;
    }

    @Transactional
    public int recoverExpiredBatch(
            Instant now,
            int batchSize
    ) {

        Objects.requireNonNull(
                now,
                "now must not be null"
        );

        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "batchSize must be greater than zero"
            );
        }

        // Lock a bounded selection of expired claims.
        List<OutboxEvent> expiredEvents =
                repository.lockExpiredBatch(now, batchSize);

        // Entities are managed by JPA in this transaction.
        for (OutboxEvent event : expiredEvents) {
            event.releaseExpiredClaim(now);
        }

        // Hibernate persists changes when the transaction commits.
        return expiredEvents.size();
    }
}
