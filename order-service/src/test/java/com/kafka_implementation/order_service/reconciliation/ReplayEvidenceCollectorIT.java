package com.kafka_implementation.order_service.reconciliation;

import com.kafka_implementation.order_service.config.MySQLTestConfiguration;
import com.kafka_implementation.order_service.domain.Order;
import com.kafka_implementation.order_service.domain.OrderStatus;
import com.kafka_implementation.order_service.domain.ProcessedEvent;
import com.kafka_implementation.order_service.repository.OrderRepository;
import com.kafka_implementation.order_service.repository.ProcessedEventRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;


import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({
        ReplayEvidenceCollector.class,
        MySQLTestConfiguration.class
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ActiveProfiles("test")
class ReplayEvidenceCollectorIT {

    @Autowired
    private ReplayEvidenceCollector collector;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldKeepConsistentSnapshotWhenAnotherTransactionCommits()
            throws Exception {

        // ARRANGE
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        TransactionTemplate writeTransaction =
                new TransactionTemplate(transactionManager);

        TransactionTemplate readTransaction =
                new TransactionTemplate(transactionManager);

        readTransaction.setReadOnly(true);

        // Commit the initial Order.
        writeTransaction.executeWithoutResult(status -> {

            orderRepository.save(new Order(
                    orderId,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    2,
                    new BigDecimal("19.99")
            ));
        });

        ExecutorService executor =
                Executors.newSingleThreadExecutor();

        try {

            // ACT: One read transaction spans both queries.
            readTransaction.executeWithoutResult(status -> {

                // First consistent read establishes the snapshot.
                boolean claimBefore =
                        processedEventRepository.existsById(eventId);

                assertThat(claimBefore).isFalse();

                // Another thread commits while this read
                // transaction remains open.
                Future<?> writer = executor.submit(() -> {

                    TransactionTemplate writerTransaction =
                            new TransactionTemplate(transactionManager);

                    writerTransaction.executeWithoutResult(tx -> {

                        Order order = orderRepository.findById(orderId)
                                .orElseThrow();

                        order.markCompleted();

                        processedEventRepository.save(
                                new ProcessedEvent(
                                        eventId,
                                        Instant.now()
                                )
                        );
                    });
                });

                // Ensure the competing transaction has committed.
                try {
                    writer.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException(
                            "Competing transaction failed", e
                    );
                }

                // Second read within the ORIGINAL transaction.
                OrderStatus observedStatus =
                        orderRepository.findById(orderId)
                                .orElseThrow()
                                .getStatus();

                // Both observations should belong to the
                // original consistent snapshot.
                assertThat(observedStatus)
                        .isEqualTo(OrderStatus.CREATED);
            });

            // Now the original reader transaction has ended.
            // A new collector transaction should see the commit.
            CollectedReplayEvidence freshEvidence =
                    collector.collect(eventId, orderId);

            assertThat(freshEvidence.originalEventCommitted())
                    .isTrue();

            assertThat(freshEvidence.orderStatus())
                    .contains(OrderStatus.COMPLETED);

        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldVerifyMySQLTransactionIsolation() {

        // ARRANGE
        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        transaction.setReadOnly(true);

        // Match the collector's default propagation behaviour.
        transaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRED
        );

        // ACT
        String isolation = transaction.execute(status ->
                jdbcTemplate.queryForObject(
                        "SELECT @@session.transaction_isolation",
                        String.class
                )
        );

        // ASSERT
        assertThat(isolation)
                .isEqualToIgnoringCase("REPEATABLE-READ");
    }

    @Test
    void shouldNotObserveUncommittedClaimOrOrderTransition()
            throws Exception {

        // ARRANGE
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        // Establish the committed baseline.
        transaction.executeWithoutResult(status -> {

            Order order = new Order(
                    orderId,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    2,
                    new BigDecimal("19.99")
            );

            orderRepository.save(order);
        });

        CountDownLatch changesFlushed = new CountDownLatch(1);
        CountDownLatch releaseTransaction = new CountDownLatch(1);

        ExecutorService executor =
                Executors.newSingleThreadExecutor();

        try {

            // Transaction A runs on a separate thread.
            Future<?> writer = executor.submit(() -> {

                TransactionTemplate writerTransaction =
                        new TransactionTemplate(transactionManager);

                writerTransaction.executeWithoutResult(status -> {

                    Order order = orderRepository.findById(orderId)
                            .orElseThrow();

                    order.markCompleted();

                    processedEventRepository.save(
                            new ProcessedEvent(eventId, Instant.now())
                    );

                    // Send SQL to MySQL without committing.
                    orderRepository.flush();
                    processedEventRepository.flush();

                    // Tell the test that SQL has been flushed.
                    changesFlushed.countDown();

                    try {
                        // Keep Transaction A open while we read.
                        if (!releaseTransaction.await(
                                30, TimeUnit.SECONDS
                        )) {
                            throw new IllegalStateException(
                                    "Timed out waiting to release transaction"
                            );
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(
                                "Writer interrupted", e
                        );
                    }

                    // Deliberately roll back after observation.
                    status.setRollbackOnly();
                });
            });

            // Wait until Transaction A has flushed its SQL.
            boolean flushed = changesFlushed.await(
                    30, TimeUnit.SECONDS
            );

            if (!flushed) {
                // Surface an early writer failure if there was one.
                if (writer.isDone()) {
                    writer.get();
                }
                throw new AssertionError(
                        "Writer did not flush within 30 seconds"
                );
            }

            // ACT:
            // Transaction A is still open at this point.
            CollectedReplayEvidence evidence =
                    collector.collect(eventId, orderId);

            // ASSERT:
            // Uncommitted event claim must remain invisible.
            assertThat(evidence.originalEventCommitted())
                    .isFalse();

            // The collector must see the committed Order state.
            assertThat(evidence.orderStatus())
                    .contains(OrderStatus.CREATED);

            // Allow the writer transaction to roll back.
            releaseTransaction.countDown();

            // Surface any failure in the worker thread.
            writer.get(30, TimeUnit.SECONDS);

        } finally {
            // Always release the waiting transaction,
            // even if an assertion or database operation fails.
            releaseTransaction.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldNotReportRolledBackClaimOrOrderTransition() {

        // ARRANGE
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        // Transaction A:
        // Create an Order and COMMIT its initial state.
        transaction.executeWithoutResult(status -> {

            Order order = new Order(
                    orderId,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    2,
                    new BigDecimal("19.99")
            );

            orderRepository.save(order);
        });

        // Transaction B:
        // Simulate event processing that eventually fails.
        transaction.executeWithoutResult(status -> {

            Order order = orderRepository.findById(orderId)
                    .orElseThrow();

            // Attempt the business state transition.
            order.markCompleted();

            // Attempt to claim the incoming event.
            processedEventRepository.save(
                    new ProcessedEvent(eventId, Instant.now())
            );

            // Force pending SQL operations to MySQL.
            orderRepository.flush();
            processedEventRepository.flush();

            // Simulate a failure before commit.
            status.setRollbackOnly();
        });

        // ACT:
        // Collect evidence AFTER transaction B rolled back.
        CollectedReplayEvidence evidence =
                collector.collect(eventId, orderId);

        // ASSERT:
        // The event claim must not exist.
        assertThat(evidence.originalEventCommitted())
                .isFalse();

        // The Order must retain its last committed state.
        assertThat(evidence.orderStatus())
                .contains(OrderStatus.CREATED);
    }

    @Test
    void shouldCollectCommittedClaimAndCompletedOrderFromMySQL() {

        // ARRANGE
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        // Persist both records in a single database transaction.
        transaction.executeWithoutResult(status -> {

            Order order = new Order(
                    orderId,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    2,
                    new BigDecimal("19.99")
            );

            orderRepository.save(order);

            order.markCompleted();

            processedEventRepository.save(
                    new ProcessedEvent(eventId, Instant.now())
            );
        });

        // The transaction above has completed successfully.
        // Its changes should now be committed.

        // ACT
        CollectedReplayEvidence evidence =
                collector.collect(eventId, orderId);

        // ASSERT
        assertThat(evidence.originalEventCommitted())
                .isTrue();

        assertThat(evidence.orderStatus())
                .contains(OrderStatus.COMPLETED);
    }
}
