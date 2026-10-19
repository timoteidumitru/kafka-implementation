package com.kafka_implementation.order_service.outbox.repository;

import com.kafka_implementation.order_service.outbox.domain.OutboxEvent;
import com.kafka_implementation.order_service.outbox.domain.OutboxStatus;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository
        extends JpaRepository<OutboxEvent, UUID> {

    @Query(
            value = """
        SELECT *
        FROM outbox_event
        WHERE status = 'PENDING'
        ORDER BY created_at, id
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """,
            nativeQuery = true
    )
    List<OutboxEvent> lockPendingBatch(
            @Param("batchSize") int batchSize
    );

    @Modifying
    @Query(
            value = """
        UPDATE outbox_event
        SET status = 'PUBLISHED',
            published_at = CURRENT_TIMESTAMP(6)
        WHERE id = :outboxEventId
          AND claim_token = :claimToken
          AND status = 'IN_PROGRESS'
        """,
            nativeQuery = true
    )
    int markPublishedIfClaimMatches(
            @Param("outboxEventId") UUID outboxEventId,
            @Param("claimToken") UUID claimToken
    );

    @Modifying
    @Query(
            value = """
        UPDATE outbox_event
        SET attempt_count = attempt_count + 1
        WHERE id = :outboxEventId
          AND claim_token = :claimToken
          AND status = 'IN_PROGRESS'
        """,
            nativeQuery = true
    )
    int incrementAttemptIfClaimMatches(
            @Param("outboxEventId") UUID outboxEventId,
            @Param("claimToken") UUID claimToken
    );

    List<OutboxEvent> findByStatusOrderByCreatedAtAscIdAsc(
            OutboxStatus status,
            Pageable pageable
    );

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(
            OutboxStatus status
    );

    boolean existsByEventId(UUID eventId);

    List<OutboxEvent> findByAggregateId(UUID aggregateId);
}
