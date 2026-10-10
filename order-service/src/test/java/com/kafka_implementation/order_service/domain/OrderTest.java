package com.kafka_implementation.order_service.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;

class OrderTest {

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
    void shouldStartInCreatedStatus() {

        // Assert
        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void shouldNotCompleteAnAlreadyCancelledOrder() {

        order.markCancelled();

        // Act + Assert
        assertThatThrownBy(order::markCompleted)
                .isInstanceOf(IllegalStateException.class);

        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void shouldNotCancelAnAlreadyCompletedOrder() {

        order.markCompleted();

        // Act + Assert
        assertThatThrownBy(order::markCancelled)
                .isInstanceOf(IllegalStateException.class);

        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void shouldCompleteOrderFromCreatedStatus() {

        order.markCompleted();

        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void shouldCancelOrderFromCreatedStatus() {

        order.markCancelled();

        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }
}