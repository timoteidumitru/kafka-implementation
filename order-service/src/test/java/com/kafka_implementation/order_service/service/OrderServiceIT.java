package com.kafka_implementation.order_service.service;

import com.kafka_implementation.order_service.config.MySQLTestConfiguration;
import com.kafka_implementation.order_service.domain.Order;
import com.kafka_implementation.order_service.domain.OrderStatus;
import com.kafka_implementation.order_service.outbox.domain.OutboxEvent;
import com.kafka_implementation.order_service.outbox.domain.OutboxStatus;
import com.kafka_implementation.order_service.outbox.repository.OutboxEventRepository;
import com.kafka_implementation.order_service.outbox.service.OutboxPublicationStateService;
import com.kafka_implementation.order_service.outbox.service.OutboxWriter;
import com.kafka_implementation.order_service.repository.OrderRepository;
import com.kafka_implementation.order_service.repository.ProcessedEventRepository;
import com.kafka_implementation.shared_events.base.EventMetadata;
import com.kafka_implementation.shared_events.inventory.InventoryReservationFailedEvent;
import com.kafka_implementation.shared_events.inventory.InventoryReservedEvent;
import com.kafka_implementation.shared_events.order.OrderCreatedEvent;
import com.kafka_implementation.shared_events.payment.PaymentFailedEvent;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    @Autowired
    private OutboxPublicationStateService stateService;

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

    @Test
    void shouldRecordFailedPublicationAttemptWithoutMarkingEventPublished() {

        // Arrange: an unpublished event exists in MySQL.
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ORDER_EVENTS_V1,
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        UUID outboxId = outboxEventRepository
                .saveAndFlush(event)
                .getId();

        // Act: record a failed Kafka publication attempt.
        stateService.recordFailedAttempt(outboxId);

        // Assert: the event remains eligible for retry.
        OutboxEvent updated = outboxEventRepository
                .findById(outboxId)
                .orElseThrow();

        assertThat(updated.getStatus())
                .isEqualTo(OutboxStatus.PENDING);

        assertThat(updated.getAttemptCount())
                .isEqualTo(1);

        assertThat(updated.getPublishedAt())
                .isNull();
    }

    @Test
    void shouldRejectPublicationAcknowledgementForUnknownOutboxEvent() {

        // Arrange: generate an ID that does not exist in MySQL.
        UUID unknownOutboxId = UUID.randomUUID();

        assertThat(outboxEventRepository.existsById(unknownOutboxId))
                .isFalse();

        // Act + Assert
        assertThatThrownBy(() ->
                stateService.markPublished(unknownOutboxId)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "Outbox event not found: " + unknownOutboxId
                );
    }

    @Test
    void shouldPreservePublishedTimestampWhenAcknowledgementIsRepeated() {

        // Arrange
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ORDER_EVENTS_V1,
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        UUID outboxId = outboxEventRepository
                .saveAndFlush(event)
                .getId();

        // First successful acknowledgement
        stateService.markPublished(outboxId);

        OutboxEvent firstPublication = outboxEventRepository
                .findById(outboxId)
                .orElseThrow();

        Instant originalPublishedAt =
                firstPublication.getPublishedAt();

        assertThat(originalPublishedAt).isNotNull();

        // Act: simulate repeated acknowledgement handling
        stateService.markPublished(outboxId);

        // Assert: timestamp and state are preserved
        OutboxEvent afterRepeatedAcknowledgement =
                outboxEventRepository.findById(outboxId)
                        .orElseThrow();

        assertThat(afterRepeatedAcknowledgement.getStatus())
                .isEqualTo(OutboxStatus.PUBLISHED);

        assertThat(afterRepeatedAcknowledgement.getPublishedAt())
                .isEqualTo(originalPublishedAt);

        assertThat(afterRepeatedAcknowledgement.getAttemptCount())
                .isZero();
    }

    @Test
    void shouldPersistPublishedStatusAfterKafkaAcknowledgement() {

        // ARRANGE: a pending event exists in MySQL.
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ORDER_EVENTS_V1,
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        OutboxEvent persisted =
                outboxEventRepository.saveAndFlush(event);

        UUID outboxId = persisted.getId();

        assertThat(persisted.getStatus())
                .isEqualTo(OutboxStatus.PENDING);

        assertThat(persisted.getPublishedAt())
                .isNull();

        // ACT: Kafka has acknowledged publication.
        stateService.markPublished(outboxId);

        // ASSERT: load the committed record from MySQL.
        OutboxEvent updated = outboxEventRepository
                .findById(outboxId)
                .orElseThrow();

        assertThat(updated.getStatus())
                .isEqualTo(OutboxStatus.PUBLISHED);

        assertThat(updated.getPublishedAt())
                .isNotNull();

        assertThat(updated.getAttemptCount())
                .isZero();
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
