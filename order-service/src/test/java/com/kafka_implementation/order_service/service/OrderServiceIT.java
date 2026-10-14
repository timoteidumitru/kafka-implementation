package com.kafka_implementation.order_service.service;

import com.kafka_implementation.order_service.domain.Order;
import com.kafka_implementation.order_service.domain.OrderStatus;
import com.kafka_implementation.order_service.repository.OrderRepository;
import com.kafka_implementation.order_service.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import com.kafka_implementation.order_service.config.MySQLTestConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import com.kafka_implementation.shared_events.base.EventMetadata;
import com.kafka_implementation.shared_events.payment.PaymentFailedEvent;
import com.kafka_implementation.order_service.outbox.domain.OutboxEvent;
import com.kafka_implementation.order_service.outbox.domain.OutboxStatus;
import com.kafka_implementation.order_service.outbox.repository.OutboxEventRepository;
import com.kafka_implementation.order_service.outbox.service.OutboxWriter;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;

import static com.kafka_implementation.shared_events.topics.Topics.ORDER_EVENTS_V1;
import static org.mockito.Mockito.*;

import java.util.List;
import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({
        OrderService.class,
        IdempotencyGuard.class,
        OrderEventProcessor.class,
        OutboxWriter.class,
        JacksonAutoConfiguration.class,
        MySQLTestConfiguration.class
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
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

    @SpyBean
    private OutboxWriter outboxWriter;

    private Order order;

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

//    @BeforeEach
//    void cleanTestData() {
//        outboxEventRepository.deleteAll();
//        processedEventRepository.deleteAll();
//        orderRepository.deleteAll();
//    }

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

        // Exactly one outgoing event exists.
        List<OutboxEvent> outboxEvents =
                outboxEventRepository.findByAggregateId(created.getId());

        assertThat(outboxEvents).hasSize(1);

        OutboxEvent outgoing = outboxEvents.get(0);

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

        // Assert 3: the outgoing event also rolled back.
        assertThat(outboxEventRepository.findByAggregateId(orderId))
                .as("A failed transaction must not leave an outbox event for this Order")
                .isEmpty();
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
                .as("Cancellation must persist one outgoing event")
                .hasSize(1);

        OutboxEvent outgoing = events.get(0);

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
