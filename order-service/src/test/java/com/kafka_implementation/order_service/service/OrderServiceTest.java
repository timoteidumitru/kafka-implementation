package com.kafka_implementation.order_service.service;

import com.kafka_implementation.order_service.domain.Order;
import com.kafka_implementation.order_service.domain.OrderStatus;
import com.kafka_implementation.order_service.repository.OrderRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository repository;

    @InjectMocks
    private OrderService orderService;

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
    void shouldCreateOrder() {

        when(repository.save(order)).thenReturn(order);

        // Act
        Order result = orderService.create(order);

        // Assert
        assertThat(result).isSameAs(order);
        verify(repository).save(order);
    }

    @Test
    void shouldCancelExistingOrder() {

        // Arrange
        UUID orderId = UUID.randomUUID();

        when(repository.findById(orderId))
                .thenReturn(Optional.of(order));

        // Act
        orderService.cancel(orderId);

        // Assert
        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.CANCELLED);

        verify(repository).save(order);
    }

    @Test
    void shouldThrowWhenCancellingNonExistingOrder() {

        // Arrange
        UUID orderId = UUID.randomUUID();

        when(repository.findById(orderId))
                .thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> orderService.cancel(orderId))
                .isInstanceOf(NoSuchElementException.class);

        verify(repository, never()).save(any(Order.class));
    }

    @Test
    void shouldNotSaveWhenCompletingCancelledOrder() {

        // Arrange
        UUID orderId = UUID.randomUUID();

        order.markCancelled();

        when(repository.findById(orderId))
                .thenReturn(Optional.of(order));

        // Act + Assert
        assertThatThrownBy(() -> orderService.complete(orderId))
                .isInstanceOf(IllegalStateException.class);

        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.CANCELLED);

        verify(repository, never()).save(any(Order.class));
    }

    @Test
    void shouldCompleteExistingOrder() {

        // Arrange
        UUID orderId = UUID.randomUUID();

        when(repository.findById(orderId))
                .thenReturn(Optional.of(order));

        // Act
        orderService.complete(orderId);

        // Assert
        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.COMPLETED);

        verify(repository).findById(orderId);
        verify(repository).save(order);
    }

}
