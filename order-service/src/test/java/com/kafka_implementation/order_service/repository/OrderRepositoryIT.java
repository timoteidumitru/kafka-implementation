package com.kafka_implementation.order_service.repository;

import com.kafka_implementation.order_service.domain.Order;
import com.kafka_implementation.order_service.domain.OrderStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.annotation.DirtiesContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import com.kafka_implementation.order_service.config.MySQLTestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.transaction.TestTransaction;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import jakarta.persistence.RollbackException;

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
@ActiveProfiles("test")
class OrderRepositoryIT {

    @Autowired
    private OrderRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

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

    @Test
    void shouldRejectStaleOrderUpdate() {
        UUID orderId = order.getId();

        // Persist the initial order in the test transaction.
        repository.saveAndFlush(order);

        // Commit it so both independent EntityManagers can read it.
        TestTransaction.flagForCommit();
        TestTransaction.end();

        EntityManager managerA =
                entityManagerFactory.createEntityManager();

        EntityManager managerB =
                entityManagerFactory.createEntityManager();

        try {
            // Both managers load the same CREATED order.
            Order orderA = managerA.find(Order.class, orderId);
            Order orderB = managerB.find(Order.class, orderId);

            assertThat(orderA.getStatus())
                    .isEqualTo(OrderStatus.CREATED);
            assertThat(orderB.getStatus())
                    .isEqualTo(OrderStatus.CREATED);

            // Transaction A completes the order.
            var transactionA = managerA.getTransaction();
            transactionA.begin();

            orderA.markCompleted();

            transactionA.commit();

            // Transaction B still holds its stale copy.
            var transactionB = managerB.getTransaction();
            transactionB.begin();

            orderB.markCancelled();

            // With optimistic locking, this must fail.
            assertThatThrownBy(transactionB::commit)
                    .isInstanceOf(RollbackException.class)
                    .hasCauseInstanceOf(OptimisticLockException.class);

            // Verify that A's result remains persisted.
            entityManager.clear();

            Order persisted = repository.findById(orderId)
                    .orElseThrow();

            assertThat(persisted.getStatus())
                    .isEqualTo(OrderStatus.COMPLETED);

        } finally {
            if (managerA.getTransaction().isActive()) {
                managerA.getTransaction().rollback();
            }

            if (managerB.getTransaction().isActive()) {
                managerB.getTransaction().rollback();
            }

            managerA.close();
            managerB.close();
        }
    }

}