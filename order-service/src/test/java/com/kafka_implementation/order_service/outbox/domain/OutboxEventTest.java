package com.kafka_implementation.order_service.outbox.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class OutboxEventTest {

    @Test
    void shouldGenerateClaimTokenWhenPendingEventIsClaimed() {

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
                Instant.parse("2026-10-17T22:00:00Z");

        // Before claiming, there should be no token.
        assertThat(event.getClaimToken()).isNull();

        // Act
        event.claim(workerId, leaseExpiresAt);

        // Assert
        assertThat(event.getClaimToken()).isNotNull();

        assertThat(event.getStatus())
                .isEqualTo(OutboxStatus.IN_PROGRESS);

        assertThat(event.getClaimedBy())
                .isEqualTo(workerId);
    }

    @Test
    void shouldRejectClaimWhenEventIsAlreadyInProgress() {

        // Arrange
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "order.events.v1",
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        UUID workerA = UUID.randomUUID();
        UUID workerB = UUID.randomUUID();

        Instant firstLease =
                Instant.parse("2026-10-16T19:00:00Z");

        Instant secondLease =
                Instant.parse("2026-10-16T19:01:00Z");

        // Worker A claims successfully.
        event.claim(workerA, firstLease);

        // Act + Assert: Worker B cannot claim the same event.
        assertThatThrownBy(() ->
                event.claim(workerB, secondLease)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "Only PENDING outbox events can be claimed"
                );

        // The original ownership must remain unchanged.
        assertThat(event.getStatus())
                .isEqualTo(OutboxStatus.IN_PROGRESS);

        assertThat(event.getClaimedBy())
                .isEqualTo(workerA);

        assertThat(event.getLeaseExpiresAt())
                .isEqualTo(firstLease);
    }

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
