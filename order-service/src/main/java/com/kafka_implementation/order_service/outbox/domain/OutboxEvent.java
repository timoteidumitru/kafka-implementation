package com.kafka_implementation.order_service.outbox.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "outbox_event",
        indexes = {
                @Index(
                        name = "idx_outbox_status_created",
                        columnList = "status, created_at"
                )
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_outbox_event_event_id",
                        columnNames = "event_id"
                )
        }
)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(
            name = "payload",
            nullable = false,
            columnDefinition = "LONGTEXT"
    )
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "claimed_by")
    private UUID claimedBy;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "claim_token")
    private UUID claimToken;

    protected OutboxEvent() {
        // Required by JPA
    }

    public OutboxEvent(
            UUID eventId,
            UUID aggregateId,
            String topic,
            String eventType,
            String payload
    ) {
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.createdAt = Instant.now();
        this.attemptCount = 0;
    }

    public UUID getClaimToken() {
        return claimToken;
    }

    public void recordAttempt() {
        this.attemptCount++;
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public void releaseExpiredClaim(Instant now) {

        if (now == null) {
            throw new IllegalArgumentException(
                    "Current time must not be null"
            );
        }

        if (this.status != OutboxStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Only IN_PROGRESS outbox events can be recovered"
            );
        }

        if (this.leaseExpiresAt == null) {
            throw new IllegalStateException(
                    "IN_PROGRESS outbox event has no lease expiration"
            );
        }

        if (this.leaseExpiresAt.isAfter(now)) {
            throw new IllegalStateException(
                    "Cannot recover an outbox event with an active lease"
            );
        }

        this.status = OutboxStatus.PENDING;
        this.claimedBy = null;
        this.claimToken = null;
        this.leaseExpiresAt = null;
    }

    public UUID getClaimedBy() {
        return claimedBy;
    }

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public void claim(UUID workerId, Instant leaseExpiresAt) {

        if (workerId == null) {
            throw new IllegalArgumentException(
                    "Worker ID must not be null"
            );
        }

        if (leaseExpiresAt == null) {
            throw new IllegalArgumentException(
                    "Lease expiration must not be null"
            );
        }

        if (this.status != OutboxStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING outbox events can be claimed"
            );
        }

        this.claimToken = UUID.randomUUID();
        this.claimedBy = workerId;
        this.leaseExpiresAt = leaseExpiresAt;
        this.status = OutboxStatus.IN_PROGRESS;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getTopic() {
        return topic;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }
}
