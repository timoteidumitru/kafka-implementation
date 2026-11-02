package com.kafka_implementation.order_service.reconciliation;

import com.kafka_implementation.order_service.domain.Order;
import com.kafka_implementation.order_service.domain.OrderStatus;
import com.kafka_implementation.order_service.repository.OrderRepository;
import com.kafka_implementation.order_service.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ReplayEvidenceCollectorTest {

    private ProcessedEventRepository processedEventRepository;
    private OrderRepository orderRepository;
    private ReplayEvidenceCollector collector;

    @BeforeEach
    void setUp() {
        processedEventRepository = mock(ProcessedEventRepository.class);
        orderRepository = mock(OrderRepository.class);

        collector = new ReplayEvidenceCollector(
                processedEventRepository,
                orderRepository
        );
    }

    @Test
    void shouldReportMissingOriginalEventClaim() {

        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Order order = mock(Order.class);

        when(processedEventRepository.existsById(eventId))
                .thenReturn(false);

        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(order));

        when(order.getStatus())
                .thenReturn(OrderStatus.CREATED);

        CollectedReplayEvidence result =
                collector.collect(eventId, orderId);

        assertThat(result.originalEventCommitted()).isFalse();
        assertThat(result.orderStatus())
                .contains(OrderStatus.CREATED);
    }

    @Test
    void shouldReportMissingOrderWithoutInventingItsStatus() {

        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        when(processedEventRepository.existsById(eventId))
                .thenReturn(false);

        when(orderRepository.findById(orderId))
                .thenReturn(Optional.empty());

        CollectedReplayEvidence result =
                collector.collect(eventId, orderId);

        assertThat(result.originalEventCommitted()).isFalse();
        assertThat(result.orderStatus()).isEmpty();
    }

    @Test
    void shouldPropagateDatabaseFailureInsteadOfTreatingItAsMissingEvidence() {

        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        when(processedEventRepository.existsById(eventId))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException(
                        "Simulated database outage"
                ));

        assertThatThrownBy(() -> collector.collect(eventId, orderId))
                .isInstanceOf(
                        org.springframework.dao.DataAccessResourceFailureException.class
                );

        verify(orderRepository, never()).findById(any());
    }

    @Test
    void shouldCollectCommittedClaimAndCurrentOrderStatus() {

        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Order order = mock(Order.class);

        when(order.getStatus()).thenReturn(OrderStatus.COMPLETED);
        when(processedEventRepository.existsById(eventId))
                .thenReturn(true);
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(order));

        CollectedReplayEvidence result =
                collector.collect(eventId, orderId);

        assertThat(result.originalEventCommitted()).isTrue();
        assertThat(result.orderStatus())
                .contains(OrderStatus.COMPLETED);

        verify(processedEventRepository).existsById(eventId);
        verify(orderRepository).findById(orderId);
    }
}
