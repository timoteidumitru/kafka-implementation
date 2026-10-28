package com.kafka_implementation.order_service.consumer;

import com.kafka_implementation.order_service.service.OrderEventProcessor;
import com.kafka_implementation.shared_events.base.DomainEvent;
import com.kafka_implementation.shared_events.base.EventMetadata;
import com.kafka_implementation.shared_events.inventory.InventoryReservationFailedEvent;
import com.kafka_implementation.shared_events.inventory.InventoryReservedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;

import java.util.Arrays;
import java.util.UUID;

import static com.kafka_implementation.shared_events.topics.Topics.INVENTORY_EVENTS_V1;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderEventRoutingTest {

    private OrderEventProcessor processor;
    private OrderEventListener listener;

    @BeforeEach
    void setUp() {
        processor = mock(OrderEventProcessor.class);
        listener = new OrderEventListener(processor);
    }

    @Test
    void shouldRejectUnsupportedInventoryEventWithoutProcessing() {

        // ARRANGE: A valid DomainEvent that is not one of
        // the inventory event types supported by this listener.
        DomainEvent unsupportedEvent = mock(DomainEvent.class);

        EventMetadata metadata =
                EventMetadata.create("inventory-service", 1);

        UUID orderId = UUID.randomUUID();

        when(unsupportedEvent.metadata()).thenReturn(metadata);
        when(unsupportedEvent.getAggregateId()).thenReturn(orderId);

        // ACT + ASSERT: The listener must throw the dedicated
        // exception that KafkaConsumerConfig treats as non-retryable.
        assertThatThrownBy(
                () -> listener.onInventoryEvent(unsupportedEvent)
        )
                .isInstanceOf(UnsupportedInventoryEventException.class)
                .hasMessageContaining("Unsupported inventory event type");

        // An unsupported event must never trigger business processing.
        verifyNoInteractions(processor);
    }

    @Test
    void shouldHaveExactlyOneListenerForInventoryTopic() {

        long count = Arrays.stream(
                        OrderEventListener.class.getDeclaredMethods()
                )
                .map(method ->
                        method.getAnnotation(KafkaListener.class)
                )
                .filter(annotation -> annotation != null)
                .filter(annotation ->
                        Arrays.asList(annotation.topics())
                                .contains(INVENTORY_EVENTS_V1)
                )
                .count();

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldRouteReservedEventToReservationProcessor() {

        InventoryReservedEvent event = new InventoryReservedEvent(
                EventMetadata.create("inventory-service", 1),
                UUID.randomUUID(),
                UUID.randomUUID(),
                2
        );

        listener.onInventoryEvent(event);

        verify(processor).processInventoryReserved(event);
        verifyNoMoreInteractions(processor);
    }

    @Test
    void shouldRouteFailedEventToFailureProcessor() {

        InventoryReservationFailedEvent event =
                new InventoryReservationFailedEvent(
                        EventMetadata.create("inventory-service", 1),
                        UUID.randomUUID(),
                        "Insufficient stock"
                );

        listener.onInventoryEvent(event);

        verify(processor).processInventoryFailure(event);
        verifyNoMoreInteractions(processor);
    }

    @Test
    void shouldRejectNullInventoryEvent() {

        assertThatThrownBy(
                () -> listener.onInventoryEvent(null)
        ).isInstanceOf(NullPointerException.class);

        verifyNoInteractions(processor);
    }
}
