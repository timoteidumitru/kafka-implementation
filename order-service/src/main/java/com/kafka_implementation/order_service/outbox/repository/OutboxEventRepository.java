package com.kafka_implementation.order_service.outbox.repository;

import com.kafka_implementation.order_service.outbox.domain.OutboxEvent;
import com.kafka_implementation.order_service.outbox.domain.OutboxStatus;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository
        extends JpaRepository<OutboxEvent, UUID> {

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
