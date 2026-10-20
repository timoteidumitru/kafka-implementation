package com.kafka_implementation.order_service.service;

import com.kafka_implementation.order_service.config.MySQLTestConfiguration;
import com.kafka_implementation.order_service.domain.Order;
import com.kafka_implementation.order_service.domain.OrderStatus;
import com.kafka_implementation.order_service.outbox.domain.OutboxEvent;
import com.kafka_implementation.order_service.outbox.domain.OutboxStatus;
import com.kafka_implementation.order_service.outbox.repository.OutboxEventRepository;
import com.kafka_implementation.order_service.outbox.service.OutboxClaimService;
import com.kafka_implementation.order_service.outbox.service.OutboxPublicationStateService;
import com.kafka_implementation.order_service.outbox.service.OutboxRecoveryService;
import com.kafka_implementation.order_service.outbox.service.OutboxWriter;
import com.kafka_implementation.order_service.repository.OrderRepository;
import com.kafka_implementation.order_service.repository.ProcessedEventRepository;
import com.kafka_implementation.shared_events.base.EventMetadata;
import com.kafka_implementation.shared_events.inventory.InventoryReservationFailedEvent;
import com.kafka_implementation.shared_events.inventory.InventoryReservedEvent;
import com.kafka_implementation.shared_events.order.OrderCreatedEvent;
import com.kafka_implementation.shared_events.payment.PaymentFailedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;

import static com.kafka_implementation.shared_events.topics.Topics.ORDER_EVENTS_V1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import({
        OrderService.class,
        IdempotencyGuard.class,
        OrderEventProcessor.class,
        OutboxWriter.class,
        OutboxPublicationStateService.class,
        JacksonAutoConfiguration.class,
        MySQLTestConfiguration.class,
        OutboxClaimService.class,
        OutboxRecoveryService.class
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
class OrderServiceIT {

    @Autowired
    private OrderService orderService;

    @Autowired
    private FailingOrderWorkflow failingOrderWorkflow;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private OrderEventProcessor orderEventProcessor;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxPublicationStateService stateService;

    @SpyBean
    private OutboxWriter outboxWriter;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private OutboxClaimService outboxClaimService;

    @Autowired
    private OutboxRecoveryService outboxRecoveryService;

    private Order order;

    private OutboxEvent event;

    @BeforeEach
    void setUp() {
        order = new Order(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                new BigDecimal("19.99")
        );
    }

    @Test
    void shouldRejectStalePublicationAfterRecoveryAndReclaim() {

        // Arrange: Worker A claims an event.
        Instant now = Instant.parse("2026-10-20T12:00:00Z");

        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ORDER_EVENTS_V1,
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        UUID workerA = UUID.randomUUID();
        UUID workerB = UUID.randomUUID();

        event.claim(workerA, now.minusSeconds(60));

        UUID oldToken = event.getClaimToken();

        UUID outboxId = outboxEventRepository
                .saveAndFlush(event)
                .getId();

        entityManager.clear();

        // Step 1: recover Worker A's expired claim.
        int recovered = outboxRecoveryService
                .recoverExpiredBatch(now, 1);

        assertThat(recovered).isEqualTo(1);

        // Step 2: Worker B claims the recovered event.
        List<OutboxEvent> claimed = outboxClaimService
                .claimPendingBatch(
                        workerB,
                        now.plusSeconds(60),
                        1
                );

        assertThat(claimed).hasSize(1);

        UUID newToken = claimed.get(0).getClaimToken();

        assertThat(newToken)
                .isNotNull()
                .isNotEqualTo(oldToken);

        // Step 3: Worker A's stale Kafka ACK arrives.
        boolean stalePublished =
                stateService.markPublished(
                        outboxId,
                        oldToken
                );

        // Old ownership must be rejected.
        assertThat(stalePublished).isFalse();

        // Also reject a stale failure callback.
        boolean staleFailure =
                stateService.recordFailedAttempt(
                        outboxId,
                        oldToken
                );

        assertThat(staleFailure).isFalse();

        entityManager.clear();

        OutboxEvent reloaded = outboxEventRepository
                .findById(outboxId)
                .orElseThrow();

        // Worker B must still own the event.
        assertThat(reloaded.getStatus())
                .isEqualTo(OutboxStatus.IN_PROGRESS);

        assertThat(reloaded.getClaimedBy())
                .isEqualTo(workerB);

        assertThat(reloaded.getClaimToken())
                .isEqualTo(newToken);

        assertThat(reloaded.getPublishedAt()).isNull();
        assertThat(reloaded.getAttemptCount()).isZero();

        // Step 4: Worker B's legitimate ACK arrives.
        boolean validPublished =
                stateService.markPublished(
                        outboxId,
                        newToken
                );

        assertThat(validPublished).isTrue();

        entityManager.clear();

        OutboxEvent published = outboxEventRepository
                .findById(outboxId)
                .orElseThrow();

        assertThat(published.getStatus())
                .isEqualTo(OutboxStatus.PUBLISHED);

        assertThat(published.getPublishedAt()).isNotNull();
    }

    @Test
    void shouldAllowOnlyOneWorkerToRecoverSameExpiredEvent()
            throws Exception {

        // Arrange: one expired event in MySQL.
        Instant now = Instant.parse("2026-10-20T12:00:00Z");

        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ORDER_EVENTS_V1,
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        UUID originalEventId = event.getEventId();

        event.claim(
                UUID.randomUUID(),
                now.minusSeconds(60)
        );

        UUID outboxId = outboxEventRepository
                .saveAndFlush(event)
                .getId();

        entityManager.clear();

        // Two independent worker threads.
        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Callable<Integer> recoveryTask = () -> {

                // Signal that this worker is ready.
                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Timed out waiting for recovery start"
                    );
                }

                // Invoke the real Spring recovery service.
                return outboxRecoveryService
                        .recoverExpiredBatch(now, 1);
            };

            Future<Integer> workerA =
                    executor.submit(recoveryTask);

            Future<Integer> workerB =
                    executor.submit(recoveryTask);

            // Both workers are ready to begin.
            assertThat(
                    ready.await(10, TimeUnit.SECONDS)
            ).isTrue();

            // Release both workers together.
            start.countDown();

            int recoveredByA =
                    workerA.get(10, TimeUnit.SECONDS);

            int recoveredByB =
                    workerB.get(10, TimeUnit.SECONDS);

            // Across both workers, the event
            // must be recovered exactly once.
            assertThat(recoveredByA + recoveredByB)
                    .isEqualTo(1);

            assertThat(List.of(recoveredByA, recoveredByB))
                    .containsExactlyInAnyOrder(0, 1);

            entityManager.clear();

            // Verify the final database state.
            OutboxEvent reloaded = outboxEventRepository
                    .findById(outboxId)
                    .orElseThrow();

            assertThat(reloaded.getStatus())
                    .isEqualTo(OutboxStatus.PENDING);

            assertThat(reloaded.getClaimedBy()).isNull();
            assertThat(reloaded.getClaimToken()).isNull();
            assertThat(reloaded.getLeaseExpiresAt()).isNull();

            // Recovery must preserve the original event.
            assertThat(reloaded.getEventId())
                    .isEqualTo(originalEventId);

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldSkipExpiredEventLockedByAnotherRecoveryWorker()
            throws Exception {

        // Arrange: one expired outbox event.
        Instant now = Instant.parse("2026-10-20T12:00:00Z");

         event = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ORDER_EVENTS_V1,
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        event.claim(
                UUID.randomUUID(),
                now.minusSeconds(60)
        );

        UUID eventId = outboxEventRepository
                .saveAndFlush(event)
                .getId();

        entityManager.clear();

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);

        try {
            // Worker A: lock the expired event and hold
            // its transaction open.
            Future<Integer> workerA = executor.submit(() -> {

                TransactionTemplate transaction =
                        new TransactionTemplate(transactionManager);

                return transaction.execute(status -> {

                    List<OutboxEvent> locked =
                            outboxEventRepository.lockExpiredBatch(
                                    now, 1
                            );

                    // Signal only after the database lock
                    // has been acquired.
                    lockAcquired.countDown();

                    try {
                        if (!releaseLock.await(
                                10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "Timed out holding the row lock"
                            );
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }

                    return locked.size();
                });
            });

            // Ensure Worker A obtained the lock.
            assertThat(
                    lockAcquired.await(10, TimeUnit.SECONDS)
            ).isTrue();

            // Worker B: attempt recovery while A
            // is still holding the row lock.
            Future<Integer> workerB = executor.submit(
                    () -> outboxRecoveryService
                            .recoverExpiredBatch(now, 1)
            );

            // B must skip the locked event.
            int recoveredByB =
                    workerB.get(10, TimeUnit.SECONDS);

            assertThat(recoveredByB).isZero();

            // Release A's lock and commit its transaction.
            releaseLock.countDown();

            assertThat(
                    workerA.get(10, TimeUnit.SECONDS)
            ).isEqualTo(1);

            // Now the event is unlocked and recoverable.
            int recoveredAfterRelease =
                    outboxRecoveryService
                            .recoverExpiredBatch(now, 1);

            assertThat(recoveredAfterRelease).isEqualTo(1);

            entityManager.clear();

            OutboxEvent reloaded = outboxEventRepository
                    .findById(eventId)
                    .orElseThrow();

            assertThat(reloaded.getStatus())
                    .isEqualTo(OutboxStatus.PENDING);

            assertThat(reloaded.getClaimToken()).isNull();
            assertThat(reloaded.getClaimedBy()).isNull();

        } finally {
            // Always release Worker A, including on failure.
            releaseLock.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRecoverOnlyRequestedNumberOfExpiredEvents() {

        // Arrange: three expired outbox events.
        Instant now = Instant.parse("2026-10-20T12:00:00Z");

        List<OutboxEvent> events = new java.util.ArrayList<>();

        for (int i = 0; i < 3; i++) {

             event = new OutboxEvent(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    ORDER_EVENTS_V1,
                    "ORDER_CREATED",
                    "{\"eventType\":\"ORDER_CREATED\"}"
            );

            event.claim(
                    UUID.randomUUID(),
                    now.minusSeconds(60 + i)
            );

            events.add(event);
        }

        outboxEventRepository.saveAllAndFlush(events);
        entityManager.clear();

        // Act: recover a maximum of two events.
        int recovered = outboxRecoveryService
                .recoverExpiredBatch(now, 2);

        // Assert: recovery respected the batch limit.
        assertThat(recovered).isEqualTo(2);

        entityManager.clear();

        List<OutboxEvent> reloaded = events.stream()
                .map(event -> outboxEventRepository
                        .findById(event.getId())
                        .orElseThrow())
                .toList();

        assertThat(reloaded.stream()
                .filter(event ->
                        event.getStatus() == OutboxStatus.PENDING)
                .count()
        ).isEqualTo(2);

        assertThat(reloaded.stream()
                .filter(event ->
                        event.getStatus() == OutboxStatus.IN_PROGRESS)
                .count()
        ).isEqualTo(1);

        // Act again: recover the remaining expired event.
        int secondBatch = outboxRecoveryService
                .recoverExpiredBatch(now, 2);

        assertThat(secondBatch).isEqualTo(1);

        entityManager.clear();

        // All three have now been recovered.
        for (OutboxEvent event : events) {

            OutboxEvent updated = outboxEventRepository
                    .findById(event.getId())
                    .orElseThrow();

            assertThat(updated.getStatus())
                    .isEqualTo(OutboxStatus.PENDING);

            assertThat(updated.getClaimToken())
                    .isNull();
        }
    }

    @Test
    void shouldRecoverExpiredEventWithoutStealingActiveClaim() {

        // Arrange: use a deterministic reference time.
        Instant now = Instant.parse("2026-10-20T12:00:00Z");

        OutboxEvent expiredEvent = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ORDER_EVENTS_V1,
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        OutboxEvent activeEvent = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ORDER_EVENTS_V1,
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        UUID expiredWorker = UUID.randomUUID();
        UUID activeWorker = UUID.randomUUID();

        Instant activeLeaseExpiry = now.plusSeconds(60);

        expiredEvent.claim(
                expiredWorker,
                now.minusSeconds(30)
        );

        activeEvent.claim(
                activeWorker,
                activeLeaseExpiry
        );

        UUID activeClaimToken = activeEvent.getClaimToken();

        outboxEventRepository.saveAllAndFlush(
                List.of(expiredEvent, activeEvent)
        );

        UUID expiredId = expiredEvent.getId();
        UUID activeId = activeEvent.getId();

        entityManager.clear();

        // Act: only expired claims should be recovered.
        int recovered = outboxRecoveryService
                .recoverExpiredBatch(now, 10);

        // Assert
        assertThat(recovered).isEqualTo(1);

        entityManager.clear();

        OutboxEvent recoveredEvent = outboxEventRepository
                .findById(expiredId)
                .orElseThrow();

        OutboxEvent stillActive = outboxEventRepository
                .findById(activeId)
                .orElseThrow();

        // Expired event is available again.
        assertThat(recoveredEvent.getStatus())
                .isEqualTo(OutboxStatus.PENDING);

        assertThat(recoveredEvent.getClaimedBy()).isNull();
        assertThat(recoveredEvent.getClaimToken()).isNull();
        assertThat(recoveredEvent.getLeaseExpiresAt()).isNull();

        // Active event must preserve its ownership.
        assertThat(stillActive.getStatus())
                .isEqualTo(OutboxStatus.IN_PROGRESS);

        assertThat(stillActive.getClaimedBy())
                .isEqualTo(activeWorker);

        assertThat(stillActive.getClaimToken())
                .isEqualTo(activeClaimToken);

        assertThat(stillActive.getLeaseExpiresAt())
                .isEqualTo(activeLeaseExpiry);
    }

    @Test
    void shouldRecoverExpiredOutboxEventFromMySQL() {

        // Arrange: create an event with an expired claim.
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ORDER_EVENTS_V1,
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        Instant now = Instant.parse("2026-10-20T12:00:00Z");
        Instant expiredLease = now.minusSeconds(30);

        event.claim(UUID.randomUUID(), expiredLease);

        UUID originalEventId = event.getEventId();

        UUID outboxId = outboxEventRepository
                .saveAndFlush(event)
                .getId();

        entityManager.clear();

        // Act: recover at most ten abandoned events.
        int recovered = outboxRecoveryService
                .recoverExpiredBatch(now, 10);

        // Assert: exactly one event was recovered.
        assertThat(recovered).isEqualTo(1);

        entityManager.clear();

        OutboxEvent reloaded = outboxEventRepository
                .findById(outboxId)
                .orElseThrow();

        assertThat(reloaded.getStatus())
                .isEqualTo(OutboxStatus.PENDING);

        assertThat(reloaded.getClaimedBy()).isNull();
        assertThat(reloaded.getClaimToken()).isNull();
        assertThat(reloaded.getLeaseExpiresAt()).isNull();

        assertThat(reloaded.getEventId())
                .isEqualTo(originalEventId);

        assertThat(reloaded.getPublishedAt()).isNull();
        assertThat(reloaded.getAttemptCount()).isZero();
    }

    @Test
    void shouldIncrementFailedAttemptWhenClaimTokenMatches() {

        // Arrange: create and claim an outbox event.
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "order.events.v1",
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        event.claim(
                UUID.randomUUID(),
                Instant.now()
                        .plusSeconds(60)
                        .truncatedTo(ChronoUnit.MICROS)
        );

        UUID validClaimToken = event.getClaimToken();

        UUID outboxId = outboxEventRepository
                .saveAndFlush(event)
                .getId();

        entityManager.clear();

        // Act: the legitimate worker records a Kafka failure.
        boolean updated = stateService.recordFailedAttempt(
                outboxId,
                validClaimToken
        );

        // Assert: the database accepted the update.
        assertThat(updated).isTrue();

        entityManager.clear();

        OutboxEvent reloaded = outboxEventRepository
                .findById(outboxId)
                .orElseThrow();

        assertThat(reloaded.getAttemptCount())
                .isEqualTo(1);

        assertThat(reloaded.getStatus())
                .isEqualTo(OutboxStatus.IN_PROGRESS);

        assertThat(reloaded.getClaimToken())
                .isEqualTo(validClaimToken);

        assertThat(reloaded.getPublishedAt())
                .isNull();
    }

    @Test
    void shouldRejectStaleClaimTokenWhenRecordingFailedAttempt() {

        // Arrange: create and claim an outbox event.
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "order.events.v1",
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        event.claim(
                UUID.randomUUID(),
                Instant.now()
                        .plusSeconds(60)
                        .truncatedTo(ChronoUnit.MICROS)
        );

        UUID currentClaimToken = event.getClaimToken();
        UUID staleClaimToken = UUID.randomUUID();

        assertThat(staleClaimToken)
                .isNotEqualTo(currentClaimToken);

        UUID outboxId = outboxEventRepository
                .saveAndFlush(event)
                .getId();

        entityManager.clear();

        // Act: a stale worker attempts to record a Kafka failure.
        boolean updated = stateService.recordFailedAttempt(
                outboxId,
                staleClaimToken
        );

        // Assert: stale ownership must be rejected.
        assertThat(updated).isFalse();

        entityManager.clear();

        OutboxEvent reloaded = outboxEventRepository
                .findById(outboxId)
                .orElseThrow();

        assertThat(reloaded.getStatus())
                .isEqualTo(OutboxStatus.IN_PROGRESS);

        assertThat(reloaded.getClaimToken())
                .isEqualTo(currentClaimToken);

        assertThat(reloaded.getAttemptCount())
                .isZero();

        assertThat(reloaded.getPublishedAt())
                .isNull();
    }

    @Test
    void shouldRejectDuplicatePublicationAcknowledgement() {

        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "order.events.v1",
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        event.claim(
                UUID.randomUUID(),
                Instant.now()
                        .plusSeconds(60)
                        .truncatedTo(ChronoUnit.MICROS)
        );

        UUID claimToken = event.getClaimToken();

        UUID outboxId = outboxEventRepository
                .saveAndFlush(event)
                .getId();

        entityManager.clear();

        // First acknowledgement: accepted.
        boolean firstResult = stateService.markPublished(
                outboxId, claimToken
        );

        assertThat(firstResult).isTrue();

        OutboxEvent firstPublication = outboxEventRepository
                .findById(outboxId)
                .orElseThrow();

        Instant firstPublishedAt =
                firstPublication.getPublishedAt();

        assertThat(firstPublishedAt).isNotNull();

        entityManager.clear();

        // Duplicate acknowledgement: rejected.
        boolean secondResult = stateService.markPublished(
                outboxId, claimToken
        );

        assertThat(secondResult).isFalse();

        entityManager.clear();

        // Published state and timestamp must remain unchanged.
        OutboxEvent reloaded = outboxEventRepository
                .findById(outboxId)
                .orElseThrow();

        assertThat(reloaded.getStatus())
                .isEqualTo(OutboxStatus.PUBLISHED);

        assertThat(reloaded.getPublishedAt())
                .isEqualTo(firstPublishedAt);

        assertThat(reloaded.getClaimToken())
                .isEqualTo(claimToken);
    }

    @Test
    void shouldMarkPublishedWhenClaimTokenMatches() {

        // Arrange: create an event and assign its ownership.
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "order.events.v1",
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        UUID workerId = UUID.randomUUID();

        event.claim(
                workerId,
                Instant.now()
                        .plusSeconds(60)
                        .truncatedTo(ChronoUnit.MICROS)
        );

        UUID validClaimToken = event.getClaimToken();

        UUID outboxId = outboxEventRepository
                .saveAndFlush(event)
                .getId();

        entityManager.clear();

        // Act: current owner acknowledges successful publication.
        boolean updated = stateService.markPublished(
                outboxId,
                validClaimToken
        );

        // Assert: exactly one active claim should be updated.
        assertThat(updated).isTrue();

        entityManager.clear();

        OutboxEvent reloaded = outboxEventRepository
                .findById(outboxId)
                .orElseThrow();

        assertThat(reloaded.getStatus())
                .isEqualTo(OutboxStatus.PUBLISHED);

        assertThat(reloaded.getPublishedAt())
                .isNotNull();

        assertThat(reloaded.getClaimToken())
                .isEqualTo(validClaimToken);
    }

    @Test
    void shouldRejectStaleClaimTokenWhenMarkingPublished() {

        // Arrange: an event is claimed by the current worker.
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "order.events.v1",
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        UUID currentWorker = UUID.randomUUID();

        event.claim(
                currentWorker,
                Instant.now()
                        .plusSeconds(60)
                        .truncatedTo(ChronoUnit.MICROS)
        );

        UUID currentClaimToken = event.getClaimToken();
        UUID staleClaimToken = UUID.randomUUID();

        assertThat(staleClaimToken)
                .isNotEqualTo(currentClaimToken);

        UUID outboxId = outboxEventRepository
                .saveAndFlush(event)
                .getId();

        entityManager.clear();

        // Act: a worker presents a token it does not own.
        boolean updated =
                stateService.markPublished(
                        outboxId,
                        staleClaimToken
                );

        // Assert: the stale worker must not change the event.
        assertThat(updated).isFalse();

        entityManager.clear();

        OutboxEvent reloaded = outboxEventRepository
                .findById(outboxId)
                .orElseThrow();

        assertThat(reloaded.getStatus())
                .isEqualTo(OutboxStatus.IN_PROGRESS);

        assertThat(reloaded.getClaimToken())
                .isEqualTo(currentClaimToken);

        assertThat(reloaded.getPublishedAt())
                .isNull();
    }

    @Test
    void shouldPreventTwoWorkersFromClaimingSameOutboxEvent()
            throws Exception {

        // Arrange: one pending event, committed to MySQL.
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "order.events.v1",
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        UUID outboxId = outboxEventRepository
                .saveAndFlush(event)
                .getId();

        UUID workerA = UUID.randomUUID();
        UUID workerB = UUID.randomUUID();

        Instant leaseExpiresAt = Instant.now()
                .plusSeconds(60)
                .truncatedTo(ChronoUnit.MICROS);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch workersReady = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<List<OutboxEvent>> resultA = executor.submit(() -> {
                workersReady.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Start timed out");
                }

                return outboxClaimService.claimPendingBatch(
                        workerA, leaseExpiresAt, 1
                );
            });

            Future<List<OutboxEvent>> resultB = executor.submit(() -> {
                workersReady.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Start timed out");
                }

                return outboxClaimService.claimPendingBatch(
                        workerB, leaseExpiresAt, 1
                );
            });

            assertThat(workersReady.await(10, TimeUnit.SECONDS))
                    .isTrue();

            // Let both workers attempt claiming.
            start.countDown();

            List<OutboxEvent> claimedA =
                    resultA.get(15, TimeUnit.SECONDS);

            List<OutboxEvent> claimedB =
                    resultB.get(15, TimeUnit.SECONDS);

            // Exactly one claim across both workers.
            assertThat(claimedA.size() + claimedB.size())
                    .isEqualTo(1);

            // Verify the actual committed database ownership.
            entityManager.clear();

            OutboxEvent reloaded = outboxEventRepository
                    .findById(outboxId)
                    .orElseThrow();

            assertThat(reloaded.getStatus())
                    .isEqualTo(OutboxStatus.IN_PROGRESS);

            assertThat(reloaded.getClaimToken())
                    .isNotNull();

            UUID expectedOwner =
                    claimedA.isEmpty() ? workerB : workerA;

            assertThat(reloaded.getClaimedBy())
                    .isEqualTo(expectedOwner);

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldAtomicallyClaimPendingOutboxEvent() {

        // Arrange
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "order.events.v1",
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        UUID outboxId = outboxEventRepository
                .saveAndFlush(event)
                .getId();

        UUID workerId = UUID.randomUUID();

        Instant leaseExpiresAt = Instant.now()
                .plusSeconds(60)
                .truncatedTo(ChronoUnit.MICROS);

        // Act: claiming service must manage its own transaction.
        List<OutboxEvent> claimed =
                outboxClaimService.claimPendingBatch(
                        workerId,
                        leaseExpiresAt,
                        1
                );

        // Assert: exactly one record was claimed.
        assertThat(claimed).hasSize(1);

        assertThat(claimed.get(0).getId())
                .isEqualTo(outboxId);

        // Read the committed state from MySQL.
        entityManager.clear();

        OutboxEvent reloaded = outboxEventRepository
                .findById(outboxId)
                .orElseThrow();

        assertThat(reloaded.getStatus())
                .isEqualTo(OutboxStatus.IN_PROGRESS);

        assertThat(reloaded.getClaimedBy())
                .isEqualTo(workerId);

        assertThat(reloaded.getClaimToken())
                .isNotNull();

        assertThat(reloaded.getLeaseExpiresAt())
                .isEqualTo(leaseExpiresAt);
    }

    @Test
    void shouldSkipOutboxEventLockedByAnotherTransaction() throws Exception {

        // Arrange: persist the event before starting the workers.
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "order.events.v1",
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        UUID eventId = outboxEventRepository.saveAndFlush(event).getId();

        // Coordinates the two transactions.
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);

        ExecutorService executor = Executors.newSingleThreadExecutor();

        TransactionTemplate transactions =
                new TransactionTemplate(transactionManager);

        Future<UUID> workerA = null;

        try {
            // Worker A starts its own transaction.
            workerA = executor.submit(() ->
                    transactions.execute(status -> {

                        List<OutboxEvent> selected =
                                outboxEventRepository.lockPendingBatch(1);

                        assertThat(selected).hasSize(1);

                        UUID lockedId = selected.get(0).getId();

                        // Tell the test that Worker A holds the lock.
                        lockAcquired.countDown();

                        try {
                            if (!releaseLock.await(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException(
                                        "Timed out waiting to release lock"
                                );
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }

                        return lockedId;
                    })
            );

            // Wait until Worker A has acquired its row lock.
            assertThat(lockAcquired.await(10, TimeUnit.SECONDS))
                    .isTrue();

            // Worker B uses another transaction and connection.
            List<OutboxEvent> workerBSelection =
                    transactions.execute(status ->
                            outboxEventRepository.lockPendingBatch(1)
                    );

            // Worker B must skip the locked event.
            assertThat(workerBSelection).isEmpty();

        } finally {
            // Allow Worker A to complete its transaction.
            releaseLock.countDown();
            executor.shutdown();
        }

        // Worker A successfully held the expected row.
        assertThat(workerA.get(10, TimeUnit.SECONDS))
                .isEqualTo(eventId);
    }

    @Test
    @Transactional
    void shouldSelectOnlyPendingOutboxEventsForClaiming() {

        // Arrange: create two pending events.
        OutboxEvent pendingOne = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "order.events.v1",
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        OutboxEvent pendingTwo = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "order.events.v1",
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        // Create an event already claimed by another worker.
        OutboxEvent inProgress = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "order.events.v1",
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        inProgress.claim(
                UUID.randomUUID(),
                Instant.now().plusSeconds(60)
        );

        outboxEventRepository.saveAll(
                List.of(pendingOne, pendingTwo, inProgress)
        );

        outboxEventRepository.flush();

        // Act: claim selection is limited to one event.
        List<OutboxEvent> selected =
                outboxEventRepository.lockPendingBatch(1);

        // Assert
        assertThat(selected).hasSize(1);

        assertThat(selected.get(0).getStatus())
                .isEqualTo(OutboxStatus.PENDING);

        assertThat(selected.get(0).getId())
                .isIn(pendingOne.getId(), pendingTwo.getId());

        assertThat(selected)
                .noneMatch(event ->
                        event.getId().equals(inProgress.getId())
                );
    }

    @Test
    void shouldPersistOutboxClaimOwnershipAndLease() {

        // ARRANGE: create a pending event.
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ORDER_EVENTS_V1,
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        UUID workerId = UUID.randomUUID();

        Instant leaseExpiresAt =
                Instant.parse("2026-10-17T21:00:00Z");

        // ACT: claim the event.
        event.claim(workerId, leaseExpiresAt);

        // Capture the token generated by claim().
        UUID originalClaimToken = event.getClaimToken();

        UUID outboxId = outboxEventRepository
                .saveAndFlush(event)
                .getId();

        // Discard managed objects so the next read
        // cannot simply return the same Java instance.
        entityManager.clear();

        // Reload from MySQL.
        OutboxEvent reloaded = outboxEventRepository
                .findById(outboxId)
                .orElseThrow();

        // ASSERT: claim information survived persistence.
        assertThat(reloaded.getStatus())
                .isEqualTo(OutboxStatus.IN_PROGRESS);

        assertThat(reloaded.getClaimedBy())
                .isEqualTo(workerId);

        assertThat(reloaded.getLeaseExpiresAt())
                .isEqualTo(leaseExpiresAt);

        // verify the claim token survived persistence.
        assertThat(reloaded.getClaimToken())
                .isEqualTo(originalClaimToken);
    }

    @Test
    void shouldRecordFailedPublicationAttemptWithoutMarkingEventPublished() {

        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ORDER_EVENTS_V1,
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        event.claim(
                UUID.randomUUID(),
                Instant.now()
                        .plusSeconds(60)
                        .truncatedTo(ChronoUnit.MICROS)
        );

        UUID claimToken = event.getClaimToken();

        UUID outboxId = outboxEventRepository
                .saveAndFlush(event)
                .getId();

        entityManager.clear();

        boolean updated = stateService.recordFailedAttempt(
                outboxId,
                claimToken
        );

        assertThat(updated).isTrue();

        entityManager.clear();

        OutboxEvent reloaded = outboxEventRepository
                .findById(outboxId)
                .orElseThrow();

        assertThat(reloaded.getStatus())
                .isEqualTo(OutboxStatus.IN_PROGRESS);

        assertThat(reloaded.getAttemptCount())
                .isEqualTo(1);

        assertThat(reloaded.getClaimToken())
                .isEqualTo(claimToken);

        assertThat(reloaded.getPublishedAt())
                .isNull();
    }

    @Test
    void shouldRejectPublicationAcknowledgementForUnknownOutboxEvent() {

        UUID unknownOutboxId = UUID.randomUUID();
        UUID claimToken = UUID.randomUUID();

        assertThat(outboxEventRepository.existsById(unknownOutboxId))
                .isFalse();

        boolean updated = stateService.markPublished(
                unknownOutboxId,
                claimToken
        );

        assertThat(updated).isFalse();
    }

    @Test
    void shouldPreservePublishedTimestampWhenAcknowledgementIsRepeated() {

        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ORDER_EVENTS_V1,
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        event.claim(
                UUID.randomUUID(),
                Instant.now()
                        .plusSeconds(60)
                        .truncatedTo(ChronoUnit.MICROS)
        );

        UUID claimToken = event.getClaimToken();

        UUID outboxId = outboxEventRepository
                .saveAndFlush(event)
                .getId();

        entityManager.clear();

        // First acknowledgement succeeds.
        boolean firstResult = stateService.markPublished(
                outboxId,
                claimToken
        );

        assertThat(firstResult).isTrue();

        entityManager.clear();

        OutboxEvent firstPublication = outboxEventRepository
                .findById(outboxId)
                .orElseThrow();

        Instant originalPublishedAt =
                firstPublication.getPublishedAt();

        assertThat(originalPublishedAt).isNotNull();

        entityManager.clear();

        // Second acknowledgement is rejected.
        boolean secondResult = stateService.markPublished(
                outboxId,
                claimToken
        );

        assertThat(secondResult).isFalse();

        entityManager.clear();

        OutboxEvent reloaded = outboxEventRepository
                .findById(outboxId)
                .orElseThrow();

        assertThat(reloaded.getStatus())
                .isEqualTo(OutboxStatus.PUBLISHED);

        assertThat(reloaded.getPublishedAt())
                .isEqualTo(originalPublishedAt);

        assertThat(reloaded.getAttemptCount())
                .isZero();
    }

    @Test
    void shouldPersistPublishedStatusAfterKafkaAcknowledgement() {

        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ORDER_EVENTS_V1,
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        event.claim(
                UUID.randomUUID(),
                Instant.now()
                        .plusSeconds(60)
                        .truncatedTo(ChronoUnit.MICROS)
        );

        UUID claimToken = event.getClaimToken();

        UUID outboxId = outboxEventRepository
                .saveAndFlush(event)
                .getId();

        entityManager.clear();

        boolean updated = stateService.markPublished(
                outboxId,
                claimToken
        );

        assertThat(updated).isTrue();

        entityManager.clear();

        OutboxEvent reloaded = outboxEventRepository
                .findById(outboxId)
                .orElseThrow();

        assertThat(reloaded.getStatus())
                .isEqualTo(OutboxStatus.PUBLISHED);

        assertThat(reloaded.getPublishedAt())
                .isNotNull();

        assertThat(reloaded.getAttemptCount())
                .isZero();

        assertThat(reloaded.getClaimToken())
                .isEqualTo(claimToken);
    }

    @Test
    void shouldFetchOnlyPendingOutboxEventsInBoundedBatch() {

        UUID orderId = UUID.randomUUID();

        OutboxEvent first = new OutboxEvent(
                UUID.randomUUID(),
                orderId,
                ORDER_EVENTS_V1,
                "ORDER_CREATED",
                "{\"test\":1}"
        );

        OutboxEvent second = new OutboxEvent(
                UUID.randomUUID(),
                orderId,
                ORDER_EVENTS_V1,
                "ORDER_FAILED",
                "{\"test\":2}"
        );

        OutboxEvent third = new OutboxEvent(
                UUID.randomUUID(),
                orderId,
                ORDER_EVENTS_V1,
                "ORDER_COMPLETED",
                "{\"test\":3}"
        );

        OutboxEvent alreadyPublished = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ORDER_EVENTS_V1,
                "ORDER_CREATED",
                "{\"test\":4}"
        );

        alreadyPublished.markPublished();

        outboxEventRepository.saveAllAndFlush(
                List.of(first, second, third, alreadyPublished)
        );

        List<OutboxEvent> batch =
                outboxEventRepository
                        .findByStatusOrderByCreatedAtAscIdAsc(
                                OutboxStatus.PENDING,
                                PageRequest.of(0, 2)
                        );

        assertThat(batch)
                .as("The relay must fetch no more than two events")
                .hasSize(2);

        assertThat(batch)
                .allMatch(event ->
                        event.getStatus() == OutboxStatus.PENDING
                );

        assertThat(batch)
                .extracting(OutboxEvent::getEventId)
                .doesNotContain(alreadyPublished.getEventId());

        assertThat(batch.get(0).getCreatedAt())
                .isBeforeOrEqualTo(batch.get(1).getCreatedAt());
    }

    @BeforeEach
    void cleanTestData() {
        outboxEventRepository.deleteAll();
        processedEventRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    void shouldRollbackOrderCreationWhenOutboxWritingFails() {

        // Arrange: identify the new Order before saving it.
        UUID orderId = order.getId();

        // Simulate a failure AFTER executing the real outbox writer.
        doAnswer(invocation -> {
            invocation.callRealMethod();

            throw new IllegalStateException(
                    "Simulated outbox failure"
            );
        }).when(outboxWriter).store(
                any(UUID.class),
                eq(ORDER_EVENTS_V1),
                any(OrderCreatedEvent.class)
        );

        // Act: the entire transaction must fail.
        assertThatThrownBy(() -> orderService.create(order))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Simulated outbox failure");

        // Assert: the Order insert must have rolled back.
        assertThat(orderRepository.findById(orderId))
                .isEmpty();

        // The outbox insert must have rolled back too.
        assertThat(outboxEventRepository.findByAggregateId(orderId))
                .isEmpty();
    }

    @Test
    void shouldPersistOutboxEventWhenOrderIsCreated() {

        // Act
        Order created = orderService.create(order);

        // Assert: the Order exists in MySQL.
        assertThat(orderRepository.findById(created.getId()))
                .isPresent();

        // The same business operation must persist an outbox event.
        List<OutboxEvent> events =
                outboxEventRepository.findByAggregateId(created.getId());

        assertThat(events)
                .as("Creating an Order must persist an outgoing event")
                .hasSize(1);

        OutboxEvent outgoing = events.get(0);

        assertThat(outgoing.getEventType())
                .isEqualTo("ORDER_CREATED");

        assertThat(outgoing.getTopic())
                .isEqualTo(ORDER_EVENTS_V1);

        assertThat(outgoing.getStatus())
                .isEqualTo(OutboxStatus.PENDING);

        assertThat(outgoing.getPublishedAt()).isNull();
        assertThat(outgoing.getAttemptCount()).isZero();
    }

    @Test
    void shouldNotCreateDuplicateOutboxEventWhenInventoryReservedIsRedelivered() {

        // Arrange
        Order created = orderService.create(order);

        InventoryReservedEvent incomingEvent = new InventoryReservedEvent(
                EventMetadata.create("inventory-service", 1),
                created.getId(),
                created.getProductId(),
                created.getQuantity()
        );

        // Act: simulate Kafka delivering the same event twice.
        boolean first = orderEventProcessor.processInventoryReserved(
                incomingEvent
        );

        boolean second = orderEventProcessor.processInventoryReserved(
                incomingEvent
        );

        // Assert: only the first invocation processes the event.
        assertThat(first).isTrue();
        assertThat(second).isFalse();

        // The Order remains completed.
        assertThat(orderRepository.findById(created.getId())
                .orElseThrow().getStatus())
                .isEqualTo(OrderStatus.COMPLETED);

        // The incoming event ID has been recorded.
        assertThat(processedEventRepository.existsById(
                incomingEvent.metadata().eventId()
        )).isTrue();

        // Exactly one outgoing OrderCompletedEvent exists for this Order.
        List<OutboxEvent> events =
                outboxEventRepository.findByAggregateId(created.getId());

        assertThat(events).hasSize(2);

        // Select the specific business event; list order is not guaranteed.
        List<OutboxEvent> matchingEvents = events.stream()
                .filter(event -> "ORDER_COMPLETED".equals(event.getEventType()))
                .toList();

        assertThat(matchingEvents)
                .as("Exactly one ORDER_COMPLETED event must exist")
                .hasSize(1);

        OutboxEvent outgoing = matchingEvents.get(0);

        assertThat(outgoing.getEventType())
                .isEqualTo("ORDER_COMPLETED");

        assertThat(outgoing.getTopic())
                .isEqualTo(ORDER_EVENTS_V1);

        assertThat(outgoing.getStatus())
                .isEqualTo(OutboxStatus.PENDING);

        assertThat(outgoing.getAttemptCount()).isZero();
    }

    @Test
    void shouldPersistOutboxEventWhenInventoryIsReserved() {

        // Arrange: create an Order in CREATED state.
        Order created = orderService.create(order);

        InventoryReservedEvent incomingEvent =
                new InventoryReservedEvent(
                        EventMetadata.create("inventory-service", 1),
                        created.getId(),
                        created.getProductId(),
                        created.getQuantity()
                );

        // Act: process the inventory reservation through the outbox-enabled path.
        boolean processed =
                orderEventProcessor.processInventoryReserved(incomingEvent);

        // Assert: Order state and idempotency.
        assertThat(processed).isTrue();

        assertThat(orderRepository.findById(created.getId())
                .orElseThrow().getStatus())
                .isEqualTo(OrderStatus.COMPLETED);

        assertThat(processedEventRepository.existsById(
                incomingEvent.metadata().eventId()
        )).isTrue();

        // Both creation and completion must create their respective events.
        List<OutboxEvent> events =
                outboxEventRepository.findByAggregateId(created.getId());

        assertThat(events)
                .as("Creation and completion must produce two events")
                .hasSize(2);

        // Select the specific business event; list order is not guaranteed.
        List<OutboxEvent> matchingEvents = events.stream()
                .filter(event -> "ORDER_COMPLETED".equals(event.getEventType()))
                .toList();

        assertThat(matchingEvents)
                .as("Exactly one ORDER_COMPLETED event must exist")
                .hasSize(1);

        OutboxEvent outgoing = matchingEvents.get(0);

        assertThat(outgoing.getTopic())
                .isEqualTo(ORDER_EVENTS_V1);

        assertThat(outgoing.getEventType())
                .isEqualTo("ORDER_COMPLETED");

        assertThat(outgoing.getStatus())
                .isEqualTo(OutboxStatus.PENDING);

        assertThat(outgoing.getAggregateId())
                .isEqualTo(created.getId());

        assertThat(outgoing.getAttemptCount())
                .isZero();

        assertThat(outgoing.getPublishedAt())
                .isNull();

        assertThat(outgoing.getPayload())
                .contains(created.getId().toString())
                .contains(outgoing.getEventId().toString());
    }

    @Test
    void shouldNotCreateDuplicateOutboxEventWhenInventoryFailureIsRedelivered() {

        // Arrange
        Order created = orderService.create(order);

        InventoryReservationFailedEvent incomingEvent =
                new InventoryReservationFailedEvent(
                        EventMetadata.create("inventory-service", 1),
                        created.getId(),
                        "Insufficient stock"
                );

        // Act: simulate two deliveries of the SAME incoming event.
        boolean first = orderEventProcessor.processInventoryFailure(
                incomingEvent
        );

        boolean second = orderEventProcessor.processInventoryFailure(
                incomingEvent
        );

        // Assert: only the first delivery is processed.
        assertThat(first).isTrue();
        assertThat(second).isFalse();

        // Order cancellation remains correct.
        assertThat(orderRepository.findById(created.getId())
                .orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);

        // The incoming event was recorded as processed.
        assertThat(processedEventRepository.existsById(
                incomingEvent.metadata().eventId()
        )).isTrue();

        // Two total events: creation and failure; no duplicate failure event.
        List<OutboxEvent> events =
                outboxEventRepository.findByAggregateId(created.getId());

        assertThat(events).hasSize(2);

        // Select the specific business event; list order is not guaranteed.
        List<OutboxEvent> matchingEvents = events.stream()
                .filter(event -> "ORDER_FAILED".equals(event.getEventType()))
                .toList();

        assertThat(matchingEvents)
                .as("Exactly one ORDER_FAILED event must exist")
                .hasSize(1);

        OutboxEvent outgoing = matchingEvents.get(0);

        assertThat(outgoing.getEventType())
                .isEqualTo("ORDER_FAILED");

        assertThat(outgoing.getTopic())
                .isEqualTo(ORDER_EVENTS_V1);

        assertThat(outgoing.getStatus())
                .isEqualTo(OutboxStatus.PENDING);

        assertThat(outgoing.getPayload())
                .contains("Insufficient stock");
    }

    @Test
    void shouldPersistOutboxEventWhenInventoryReservationFails() {

        Order created = orderService.create(order);

        InventoryReservationFailedEvent incomingEvent =
                new InventoryReservationFailedEvent(
                        EventMetadata.create("inventory-service", 1),
                        created.getId(),
                        "Insufficient stock"
                );

        boolean processed =
                orderEventProcessor.processInventoryFailure(incomingEvent);

        assertThat(processed).isTrue();

        assertThat(orderRepository.findById(created.getId())
                .orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);

        assertThat(processedEventRepository.existsById(
                incomingEvent.metadata().eventId()
        )).isTrue();

        List<OutboxEvent> events =
                outboxEventRepository.findByAggregateId(created.getId());

        assertThat(events).hasSize(2);

        // Select the specific business event; list order is not guaranteed.
        List<OutboxEvent> matchingEvents = events.stream()
                .filter(event -> "ORDER_FAILED".equals(event.getEventType()))
                .toList();

        assertThat(matchingEvents)
                .as("Exactly one ORDER_FAILED event must exist")
                .hasSize(1);

        OutboxEvent outgoing = matchingEvents.get(0);

        assertThat(outgoing.getTopic())
                .isEqualTo(ORDER_EVENTS_V1);

        assertThat(outgoing.getEventType())
                .isEqualTo("ORDER_FAILED");

        assertThat(outgoing.getStatus())
                .isEqualTo(OutboxStatus.PENDING);

        assertThat(outgoing.getPayload())
                .contains("Insufficient stock")
                .contains(created.getId().toString());
    }

    @Test
    void shouldNotCreateDuplicateOutboxEventForSameIncomingEvent() {

        // Arrange
        Order created = orderService.create(order);

        PaymentFailedEvent incomingEvent = new PaymentFailedEvent(
                EventMetadata.create("payment-service", 1),
                created.getId(),
                "Payment declined"
        );

        // Act: simulate Kafka delivering the same event twice.
        boolean first = orderEventProcessor.processPaymentFailure(
                incomingEvent
        );

        boolean second = orderEventProcessor.processPaymentFailure(
                incomingEvent
        );

        // Assert: only the first processing should succeed.
        assertThat(first).isTrue();
        assertThat(second).isFalse();

        // The Order transition happened.
        assertThat(orderRepository.findById(created.getId())
                .orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);

        // Incoming event was claimed exactly once.
        assertThat(processedEventRepository.existsById(
                incomingEvent.metadata().eventId()
        )).isTrue();

        // Two total events: one creation event and one failure event.
        List<OutboxEvent> outboxEvents =
                outboxEventRepository.findByAggregateId(created.getId());

        assertThat(outboxEvents).hasSize(2);

        // Select the specific business event; list order is not guaranteed.
        List<OutboxEvent> matchingEvents = outboxEvents.stream()
                .filter(event -> "ORDER_FAILED".equals(event.getEventType()))
                .toList();

        assertThat(matchingEvents)
                .as("Exactly one ORDER_FAILED event must exist")
                .hasSize(1);

        OutboxEvent outgoing = matchingEvents.get(0);

        assertThat(outgoing.getAggregateId())
                .isEqualTo(created.getId());

        assertThat(outgoing.getEventType())
                .isEqualTo("ORDER_FAILED");

        assertThat(outgoing.getStatus())
                .isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void shouldRollbackOrderAndOutboxWhenOutboxWritingFails() {

        // Arrange: create an Order with its own committed transaction.
        Order created = orderService.create(order);

        UUID orderId = created.getId();

        PaymentFailedEvent incomingEvent = new PaymentFailedEvent(
                EventMetadata.create("payment-service", 1),
                orderId,
                "Payment declined"
        );

        // Run the real outbox writer, then simulate a failure
        // before the surrounding transaction commits.
        doAnswer(invocation -> {
            invocation.callRealMethod();

            throw new IllegalStateException(
                    "Simulated failure after outbox save"
            );
        }).when(outboxWriter).store(
                any(UUID.class),
                anyString(),
                any()
        );

        // Act: the transactional processing must fail.
        assertThatThrownBy(() ->
                orderEventProcessor.processPaymentFailure(incomingEvent)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Simulated failure after outbox save");

        // Assert 1: the Order state change rolled back.
        Order persistedOrder = orderRepository
                .findById(orderId)
                .orElseThrow();

        assertThat(persistedOrder.getStatus())
                .isEqualTo(OrderStatus.CREATED);

        // Assert 2: the incoming event claim rolled back.
        assertThat(processedEventRepository.existsById(
                incomingEvent.metadata().eventId()
        )).isFalse();

        // Assert 3: the earlier ORDER_CREATED survives, but ORDER_FAILED does not.
        List<OutboxEvent> events = outboxEventRepository.findByAggregateId(orderId);

        assertThat(events)
                .as("Only the previously committed creation event must remain")
                .hasSize(1);

        assertThat(events.get(0).getEventType())
                .isEqualTo("ORDER_CREATED");

        assertThat(events)
                .noneMatch(event -> "ORDER_FAILED".equals(event.getEventType()));
    }

    @Test
    void shouldPersistOutgoingEventWhenOrderIsCancelled() {

        // Arrange
        Order created = orderService.create(order);

        PaymentFailedEvent incomingEvent = new PaymentFailedEvent(
                EventMetadata.create("payment-service", 1),
                created.getId(),
                "Payment declined"
        );

        // Act
        boolean processed = orderEventProcessor.processPaymentFailure(
                incomingEvent
        );

        // Assert: all three database changes have committed.
        assertThat(processed).isTrue();

        Order persistedOrder = orderRepository
                .findById(created.getId())
                .orElseThrow();

        assertThat(persistedOrder.getStatus())
                .isEqualTo(OrderStatus.CANCELLED);

        assertThat(processedEventRepository.existsById(
                incomingEvent.metadata().eventId()
        )).isTrue();

        List<OutboxEvent> events =
                outboxEventRepository.findByAggregateId(created.getId());

        assertThat(events)
                .as("Creation and cancellation must produce two events")
                .hasSize(2);

        // Select the specific business event; list order is not guaranteed.
        List<OutboxEvent> matchingEvents = events.stream()
                .filter(event -> "ORDER_FAILED".equals(event.getEventType()))
                .toList();

        assertThat(matchingEvents)
                .as("Exactly one ORDER_FAILED event must exist")
                .hasSize(1);

        OutboxEvent outgoing = matchingEvents.get(0);

        assertThat(outgoing.getAggregateId())
                .isEqualTo(created.getId());

        assertThat(outgoing.getTopic())
                .isEqualTo(ORDER_EVENTS_V1);

        assertThat(outgoing.getEventType())
                .isEqualTo("ORDER_FAILED");

        assertThat(outgoing.getStatus())
                .isEqualTo(OutboxStatus.PENDING);

        assertThat(outgoing.getAttemptCount()).isZero();
        assertThat(outgoing.getPublishedAt()).isNull();

        // Check the outgoing event preserves essential information.
        assertThat(outgoing.getPayload())
                .contains("Payment declined")
                .contains(created.getId().toString())
                .contains(outgoing.getEventId().toString());
    }

    @Test
    void shouldProcessEventOnlyOnce() {
        Order created = orderService.create(order);
        UUID eventId = UUID.randomUUID();

        boolean firstProcessing = orderEventProcessor.processCompletion(
                eventId,
                created.getId()
        );

        boolean secondProcessing = orderEventProcessor.processCompletion(
                eventId,
                created.getId()
        );

        assertThat(firstProcessing).isTrue();
        assertThat(secondProcessing).isFalse();

        assertThat(processedEventRepository.existsById(eventId))
                .isTrue();

        Order persisted = orderRepository.findById(created.getId())
                .orElseThrow();

        assertThat(persisted.getStatus())
                .isEqualTo(OrderStatus.COMPLETED);

        assertThat(persisted.getVersion())
                .isEqualTo(1L);
    }

    @Test
    void shouldNotMarkEventProcessedWhenBusinessProcessingFails() {
        UUID eventId = UUID.randomUUID();
        UUID nonExistentOrderId = UUID.randomUUID();

        assertThatThrownBy(() ->
                orderEventProcessor.processCancellation(
                        eventId,
                        nonExistentOrderId
                )
        ).isInstanceOf(java.util.NoSuchElementException.class);

        assertThat(processedEventRepository.existsById(eventId))
                .as("Failed order processing must roll back the event claim")
                .isFalse();
    }

    @Test
    void shouldCreateOrderAndPersistIt() {

        // Act
        Order created = orderService.create(order);

        // Assert
        Order persisted = orderRepository
                .findById(created.getId())
                .orElseThrow();

        assertThat(persisted.getId())
                .isEqualTo(order.getId());

        assertThat(persisted.getStatus())
                .isEqualTo(OrderStatus.CREATED);

        assertThat(persisted.getQuantity())
                .isEqualTo(2);

        assertThat(persisted.getPrice())
                .isEqualByComparingTo("19.99");
    }

    @Test
    void shouldCompleteOrderAndPersistStatus() {

        // Arrange
        Order created = orderService.create(order);

        // Act
        orderService.complete(created.getId());

        // Assert
        Order persisted = orderRepository
                .findById(created.getId())
                .orElseThrow();

        assertThat(persisted.getStatus())
                .isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void shouldCancelOrderAndPersistStatus() {

        // Arrange
        Order created = orderService.create(order);

        // Act
        orderService.cancel(created.getId());

        // Assert
        Order persisted = orderRepository
                .findById(created.getId())
                .orElseThrow();

        assertThat(persisted.getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void shouldRejectCompletingAlreadyCancelledOrder() {

        // Arrange
        Order created = orderService.create(order);

        orderService.cancel(created.getId());

        // Act & Assert
        assertThatThrownBy(
                () -> orderService.complete(created.getId())
        ).isInstanceOf(IllegalStateException.class);

        // Verify the original state remains unchanged
        Order persisted = orderRepository
                .findById(created.getId())
                .orElseThrow();

        assertThat(persisted.getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void shouldRejectCancellingAlreadyCompletedOrder() {

        // Arrange
        Order created = orderService.create(order);

        orderService.complete(created.getId());

        // Act & Assert
        assertThatThrownBy(
                () -> orderService.cancel(created.getId())
        ).isInstanceOf(IllegalStateException.class);

        // Verify the original state remains unchanged
        Order persisted = orderRepository
                .findById(created.getId())
                .orElseThrow();

        assertThat(persisted.getStatus())
                .isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void shouldRollbackOrderStatusWhenTransactionFails() {
        // Arrange: create and commit an order.
        Order created = orderService.create(order);
        UUID orderId = created.getId();

        // Act: modify the order, then fail inside the transaction.
        assertThatThrownBy(
                () -> failingOrderWorkflow.completeThenFail(orderId)
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Simulated failure");

        // Assert: query MySQL after the failed transaction.
        Order persisted = orderRepository.findById(orderId)
                .orElseThrow();

        assertThat(persisted.getStatus())
                .isEqualTo(OrderStatus.CREATED);
    }

    @TestConfiguration
    static class RollbackTestConfiguration {

        @Bean
        FailingOrderWorkflow failingOrderWorkflow(
                OrderRepository repository
        ) {
            return new FailingOrderWorkflow(repository);
        }
    }

    static class FailingOrderWorkflow {

        private final OrderRepository repository;

        FailingOrderWorkflow(OrderRepository repository) {
            this.repository = repository;
        }

        @Transactional
        public void completeThenFail(UUID orderId) {
            Order managedOrder = repository.findById(orderId)
                    .orElseThrow();

            managedOrder.markCompleted();

            // Simulate an unexpected failure before commit.
            throw new RuntimeException("Simulated failure");
        }
    }

}
