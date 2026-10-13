package com.kafka_implementation.order_service.repository;

import com.kafka_implementation.order_service.domain.Order;
import com.kafka_implementation.order_service.domain.OrderStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.annotation.DirtiesContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import com.kafka_implementation.order_service.config.MySQLTestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.transaction.TestTransaction;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(MySQLTestConfiguration.class)
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderRepositoryIT {

    @Autowired
    private OrderRepository repository;

    @Autowired
    private EntityManager entityManager;

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
    void shouldPersistAndReloadOrder() {

        // Arrange
        UUID orderId = order.getId();

        // Act
        repository.saveAndFlush(order);
        entityManager.clear();

        Order reloaded = repository.findById(orderId)
                .orElseThrow();

        // Assert
        assertThat(reloaded.getId()).isEqualTo(orderId);
        assertThat(reloaded.getStatus())
                .isEqualTo(OrderStatus.CREATED);
        assertThat(reloaded.getQuantity()).isEqualTo(2);
        assertThat(reloaded.getPrice())
                .isEqualByComparingTo("19.99");
    }

    @Test
    void shouldReturnEmptyWhenOrderDoesNotExist() {

        // Arrange
        UUID unknownOrderId = UUID.randomUUID();

        // Act
        Optional<Order> result =
                repository.findById(unknownOrderId);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void shouldRollbackOrderWhenTransactionIsMarkedRollbackOnly() {

        // Arrange
        UUID orderId = order.getId();

        // Act
        repository.saveAndFlush(order);

        // The Order exists within the current transaction
        assertThat(repository.findById(orderId)).isPresent();

        // Explicitly roll back the test transaction
        TestTransaction.flagForRollback();
        TestTransaction.end();

        // Query after the transaction has ended
        assertThat(repository.findById(orderId)).isEmpty();
    }

}