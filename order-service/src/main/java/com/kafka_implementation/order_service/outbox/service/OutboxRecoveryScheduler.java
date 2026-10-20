package com.kafka_implementation.order_service.outbox.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
@ConditionalOnProperty(
        prefix = "app.outbox.recovery",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class OutboxRecoveryScheduler {

    private final OutboxRecoveryService recoveryService;
    private final Clock clock;
    private final int batchSize;

    public OutboxRecoveryScheduler(
            OutboxRecoveryService recoveryService,
            Clock clock,
            @Value("${app.outbox.recovery.batch-size:100}")
            int batchSize
    ) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "Recovery batch size must be positive"
            );
        }

        this.recoveryService = recoveryService;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString =
                    "${app.outbox.recovery.fixed-delay-ms:10000}"
    )
    public void recoverExpiredEvents() {

        Instant now = clock.instant();

        recoveryService.recoverExpiredBatch(
                now,
                batchSize
        );
    }
}
