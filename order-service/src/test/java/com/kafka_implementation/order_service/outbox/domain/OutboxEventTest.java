package com.kafka_implementation.order_service.outbox.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    @Test
    void shouldClaimPendingEventForSpecificWorker() {

        // Arrange
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "order.events.v1",
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        UUID workerId = UUID.randomUUID();

        Instant leaseExpiresAt =
                Instant.parse("2026-10-16T19:00:00Z");

        assertThat(event.getStatus())
                .isEqualTo(OutboxStatus.PENDING);

        // Act
        event.claim(workerId, leaseExpiresAt);

        // Assert
        assertThat(event.getStatus())
                .isEqualTo(OutboxStatus.IN_PROGRESS);

        assertThat(event.getClaimedBy())
                .isEqualTo(workerId);

        assertThat(event.getLeaseExpiresAt())
                .isEqualTo(leaseExpiresAt);
    }
}
