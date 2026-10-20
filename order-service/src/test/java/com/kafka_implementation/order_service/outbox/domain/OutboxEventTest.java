package com.kafka_implementation.order_service.outbox.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class OutboxEventTest {

    private OutboxEvent event;

    @BeforeEach
    void setUp() {
         event = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "order.events.v1",
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );
    }

    @Test
    void shouldRejectRecoveryWhenEventIsPublished() {

        // Arrange: event has already been published.
        event.markPublished();

        Instant originalPublishedAt = event.getPublishedAt();

        assertThat(event.getStatus())
                .isEqualTo(OutboxStatus.PUBLISHED);

        Instant now = Instant.parse("2026-10-20T12:00:00Z");

        // Act + Assert
        assertThatThrownBy(() ->
                event.releaseExpiredClaim(now)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "Only IN_PROGRESS outbox events can be recovered"
                );

        // Its published state must remain intact.
        assertThat(event.getStatus())
                .isEqualTo(OutboxStatus.PUBLISHED);

        assertThat(event.getPublishedAt())
                .isEqualTo(originalPublishedAt);

        assertThat(event.getClaimToken()).isNull();
        assertThat(event.getClaimedBy()).isNull();
    }

    @Test
    void shouldRecoverClaimWhenLeaseExpiresExactlyNow() {

        // Arrange
        UUID workerId = UUID.randomUUID();

        Instant now = Instant.parse("2026-10-20T12:00:00Z");

        // Lease expires at the exact recovery timestamp.
        event.claim(workerId, now);

        assertThat(event.getStatus())
                .isEqualTo(OutboxStatus.IN_PROGRESS);

        assertThat(event.getClaimToken())
                .isNotNull();

        // Act
        event.releaseExpiredClaim(now);

        // Assert
        assertThat(event.getStatus())
                .isEqualTo(OutboxStatus.PENDING);

        assertThat(event.getClaimedBy()).isNull();
        assertThat(event.getClaimToken()).isNull();
        assertThat(event.getLeaseExpiresAt()).isNull();
    }

    @Test
    void shouldRejectRecoveryWhenEventIsPending() {

        // Arrange: a newly created event is PENDING.
        assertThat(event.getStatus())
                .isEqualTo(OutboxStatus.PENDING);

        Instant now = Instant.parse("2026-10-20T12:00:00Z");

        // Act + Assert
        assertThatThrownBy(() ->
                event.releaseExpiredClaim(now)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "Only IN_PROGRESS outbox events can be recovered"
                );

        // Rejected recovery must not modify state.
        assertThat(event.getStatus())
                .isEqualTo(OutboxStatus.PENDING);

        assertThat(event.getClaimToken()).isNull();
        assertThat(event.getClaimedBy()).isNull();
        assertThat(event.getLeaseExpiresAt()).isNull();
    }

    @Test
    void shouldGenerateNewClaimTokenAfterExpiredEventIsRecovered() {

        // Arrange: Worker A claims the event.
        UUID workerA = UUID.randomUUID();
        UUID workerB = UUID.randomUUID();

        Instant now = Instant.parse("2026-10-20T12:00:00Z");

        Instant expiredLease = now.minusSeconds(30);
        Instant newLease = now.plusSeconds(60);

        event.claim(workerA, expiredLease);

        UUID oldClaimToken = event.getClaimToken();

        // Act: recover the abandoned claim.
        event.releaseExpiredClaim(now);

        // Worker B claims the recovered event.
        event.claim(workerB, newLease);

        // Assert: new ownership is established.
        assertThat(event.getStatus())
                .isEqualTo(OutboxStatus.IN_PROGRESS);

        assertThat(event.getClaimedBy())
                .isEqualTo(workerB);

        assertThat(event.getClaimToken())
                .isNotNull()
                .isNotEqualTo(oldClaimToken);

        assertThat(event.getLeaseExpiresAt())
                .isEqualTo(newLease);
    }

    @Test
    void shouldRejectRecoveryWhenLeaseHasNotExpired() {

        UUID workerId = UUID.randomUUID();

        Instant now = Instant.parse("2026-10-20T12:00:00Z");
        Instant futureExpiry = now.plusSeconds(60);

        event.claim(workerId, futureExpiry);

        UUID originalClaimToken = event.getClaimToken();

        // Act + Assert: recovery must be rejected.
        assertThatThrownBy(() ->
                event.releaseExpiredClaim(now)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active lease");

        // Verify the rejected operation did not change ownership.
        assertThat(event.getStatus())
                .isEqualTo(OutboxStatus.IN_PROGRESS);

        assertThat(event.getClaimedBy())
                .isEqualTo(workerId);

        assertThat(event.getClaimToken())
                .isEqualTo(originalClaimToken);

        assertThat(event.getLeaseExpiresAt())
                .isEqualTo(futureExpiry);

        assertThat(event.getPublishedAt())
                .isNull();
    }

    @Test
    void shouldReleaseExpiredClaimAndRestorePendingState() {

        UUID workerId = UUID.randomUUID();

        Instant now = Instant.parse("2026-10-20T12:00:00Z");

        Instant expiredLease = now.minusSeconds(30);

        event.claim(workerId, expiredLease);

        UUID originalEventId = event.getEventId();
        UUID originalClaimToken = event.getClaimToken();

        assertThat(event.getStatus())
                .isEqualTo(OutboxStatus.IN_PROGRESS);

        assertThat(originalClaimToken).isNotNull();

        // Act: recover the abandoned event.
        event.releaseExpiredClaim(now);

        // Assert: event becomes available for claiming again.
        assertThat(event.getStatus())
                .isEqualTo(OutboxStatus.PENDING);

        assertThat(event.getClaimedBy()).isNull();
        assertThat(event.getClaimToken()).isNull();
        assertThat(event.getLeaseExpiresAt()).isNull();

        // The original event identity must remain unchanged.
        assertThat(event.getEventId())
                .isEqualTo(originalEventId);

        assertThat(event.getAttemptCount())
                .isZero();

        assertThat(event.getPublishedAt())
                .isNull();
    }

    @Test
    void shouldGenerateClaimTokenWhenPendingEventIsClaimed() {

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
