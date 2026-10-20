package com.kafka_implementation.order_service.outbox.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxRecoverySchedulerTest {

    @Test
    void shouldTriggerRecoveryWithCurrentTimeAndBatchSize() {

        // Arrange: use a deterministic clock.
        Instant now =
                Instant.parse("2026-10-20T12:00:00Z");

        Clock clock = Clock.fixed(
                now,
                ZoneOffset.UTC
        );

        OutboxRecoveryService recoveryService =
                mock(OutboxRecoveryService.class);

        OutboxRecoveryScheduler scheduler =
                new OutboxRecoveryScheduler(
                        recoveryService,
                        clock,
                        100
                );

        // Act
        scheduler.recoverExpiredEvents();

        // Assert
        verify(recoveryService)
                .recoverExpiredBatch(now, 100);
    }
}
