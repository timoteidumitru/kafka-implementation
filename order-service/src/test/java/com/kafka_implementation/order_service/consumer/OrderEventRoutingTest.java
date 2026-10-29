package com.kafka_implementation.order_service.consumer;

import com.kafka_implementation.order_service.service.OrderEventProcessor;
import com.kafka_implementation.shared_events.base.DomainEvent;
import com.kafka_implementation.shared_events.base.EventMetadata;
import com.kafka_implementation.shared_events.inventory.InventoryReservationFailedEvent;
import com.kafka_implementation.shared_events.inventory.InventoryReservedEvent;
import com.kafka_implementation.shared_events.payment.PaymentFailedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    void shouldRouteSupportedPaymentFailedEvent() {

        PaymentFailedEvent event = new PaymentFailedEvent(
                EventMetadata.create("payment-service", 1),
                UUID.randomUUID(),
                "Payment declined"
        );

        listener.onPaymentFailed(event);

        verify(processor).processPaymentFailure(event);
        verifyNoMoreInteractions(processor);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 2})
    void shouldRejectInvalidInventoryReservationFailedVersions(int version) {

        InventoryReservationFailedEvent event =
                new InventoryReservationFailedEvent(
                        EventMetadata.create("inventory-service", version),
                        UUID.randomUUID(),
                        "Insufficient stock"
                );

        assertThatThrownBy(
                () -> listener.onInventoryEvent(event)
        )
                .isInstanceOf(UnsupportedEventVersionException.class)
                .hasMessageContaining("version");

        verifyNoInteractions(processor);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 2})
    void shouldRejectInvalidInventoryReservedVersions(int version) {

        InventoryReservedEvent event = new InventoryReservedEvent(
                EventMetadata.create("inventory-service", version),
                UUID.randomUUID(),
                UUID.randomUUID(),
                5
        );

        assertThatThrownBy(
                () -> listener.onInventoryEvent(event)
        )
                .isInstanceOf(UnsupportedEventVersionException.class)
                .hasMessageContaining("version");

        verifyNoInteractions(processor);
    }

    @Test
    void shouldRejectUnsupportedPaymentFailedEventVersion() {

        // ARRANGE: Simulate an incoming PaymentFailedEvent V2.
        PaymentFailedEvent event = new PaymentFailedEvent(
                EventMetadata.create("payment-service", 2),
                UUID.randomUUID(),
                "Payment declined"
        );

        assertThat(event.getVersion()).isEqualTo(2);

        // ACT + ASSERT
        assertThatThrownBy(
                () -> listener.onPaymentFailed(event)
        )
                .isInstanceOf(UnsupportedEventVersionException.class)
                .hasMessageContaining("version");

        // Reject before invoking transactional business processing.
        verifyNoInteractions(processor);
    }

    @Test
    void shouldRejectUnsupportedInventoryEventVersion() {

        // ARRANGE: Order Service currently supports inventory schema V1.
        InventoryReservedEvent event = new InventoryReservedEvent(
                EventMetadata.create("inventory-service", 2),
                UUID.randomUUID(),
                UUID.randomUUID(),
                5
        );

        assertThat(event.getVersion()).isEqualTo(2);

        // ACT + ASSERT: V2 must not reach business processing.
        assertThatThrownBy(
                () -> listener.onInventoryEvent(event)
        )
                .isInstanceOf(UnsupportedEventVersionException.class)
                .hasMessageContaining("version");

        verifyNoInteractions(processor);
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
        when(unsupportedEvent.getVersion()).thenReturn(1);

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
