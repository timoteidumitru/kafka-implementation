package com.kafka_implementation.order_service.outbox.service;

import com.kafka_implementation.order_service.outbox.domain.OutboxEvent;
import com.kafka_implementation.order_service.outbox.domain.OutboxStatus;
import com.kafka_implementation.order_service.outbox.repository.OutboxEventRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
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
    public boolean recordFailedAttempt(
            UUID outboxEventId,
            UUID claimToken
    ) {
        Objects.requireNonNull(
                outboxEventId,
                "outboxEventId must not be null"
        );

        Objects.requireNonNull(
                claimToken,
                "claimToken must not be null"
        );

        int updatedRows =
                repository.incrementAttemptIfClaimMatches(
                        outboxEventId,
                        claimToken
                );

        return updatedRows == 1;
    }

    @Transactional
    public boolean markPublished(
            UUID outboxEventId,
            UUID claimToken
    ) {
        Objects.requireNonNull(
                outboxEventId,
                "outboxEventId must not be null"
        );

        Objects.requireNonNull(
                claimToken,
                "claimToken must not be null"
        );

        int updatedRows =
                repository.markPublishedIfClaimMatches(
                        outboxEventId,
                        claimToken
                );

        return updatedRows == 1;
    }

}
