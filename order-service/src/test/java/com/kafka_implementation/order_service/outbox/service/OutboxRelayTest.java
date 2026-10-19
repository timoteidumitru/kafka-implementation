
package com.kafka_implementation.order_service.outbox.service;

import com.kafka_implementation.order_service.outbox.domain.OutboxEvent;
import com.kafka_implementation.order_service.outbox.domain.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.concurrent.CompletionException;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxKafkaPublisher publisher;

    @Mock
    private OutboxPublicationStateService stateService;

    private OutboxRelay relay;

    private OutboxEvent event;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(publisher, stateService);

        event = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "order.events.v1",
                "ORDER_CREATED",
                "{\"eventType\":\"ORDER_CREATED\"}"
        );

        ReflectionTestUtils.setField(
                event,
                "id",
                UUID.randomUUID()
        );

        // Give the event a real claim and ownership token.
        event.claim(
                UUID.randomUUID(),
                Instant.now().plusSeconds(60)
        );
    }

    @Test
    void shouldFailWhenPublicationAcknowledgementLosesOwnership() {

        // Arrange: Kafka will acknowledge successfully.
        CompletableFuture<SendResult<String, String>> kafkaFuture =
                new CompletableFuture<>();

        when(publisher.publish(event))
                .thenReturn(kafkaFuture);

        // MySQL rejects the acknowledgement: the claim is stale.
        when(stateService.markPublished(
                event.getId(),
                event.getClaimToken()
        )).thenReturn(false);

        // Act
        CompletableFuture<Void> result = relay.publish(event);

        kafkaFuture.complete(null);

        // Assert: rejected ownership must not look successful.
        assertThat(result).isCompletedExceptionally();

        // Kafka succeeded, so this is not a Kafka send failure.

        verify(stateService, never())
                .recordFailedAttempt(
                        any(UUID.class),
                        any(UUID.class)
                );

        verify(stateService, times(1))
                .markPublished(
                        event.getId(),
                        event.getClaimToken()
                );
    }

    @Test
    void shouldMarkEventPublishedOnlyAfterKafkaAcknowledgementSucceeds() {

        // Arrange: Kafka send has not completed yet.
        CompletableFuture<SendResult<String, String>> kafkaFuture =
                new CompletableFuture<>();

        when(publisher.publish(event))
                .thenReturn(kafkaFuture);

        assertThat(event.getClaimToken()).isNotNull();

        assertThat(event.getStatus())
                .isEqualTo(OutboxStatus.IN_PROGRESS);

        when(stateService.markPublished(
                event.getId(),
                event.getClaimToken()
        )).thenReturn(true);

        // Act: relay initiates publication.
        CompletableFuture<Void> result = relay.publish(event);

        // Before Kafka ACK: the state service must not be called.
        verifyNoInteractions(stateService);

        assertThat(result).isNotDone();

        // Simulate successful acknowledgement from Kafka.
        kafkaFuture.complete(null);

        // Assert: publication update occurs only after ACK.
        assertThat(result).isCompleted();

        verify(stateService, times(1))
                .markPublished(
                        event.getId(),
                        event.getClaimToken()
                );

        verify(stateService, never())
                .recordFailedAttempt(
                        any(UUID.class),
                        any(UUID.class)
                );

        verify(publisher, times(1))
                .publish(event);
    }

    @Test
    void shouldPreserveKafkaFailureWhenRecordingFailedAttemptAlsoFails() {

        // Arrange: Kafka publication fails.
        CompletableFuture<SendResult<String, String>> kafkaFuture =
                new CompletableFuture<>();

        RuntimeException kafkaFailure =
                new RuntimeException("Kafka broker unavailable");

        RuntimeException databaseFailure =
                new RuntimeException("MySQL unavailable");

        when(publisher.publish(event))
                .thenReturn(kafkaFuture);

        // MySQL also fails while recording the Kafka failure.
        doThrow(databaseFailure)
                .when(stateService)
                .recordFailedAttempt(
                        event.getId(),
                        event.getClaimToken()
                );

        // Act
        CompletableFuture<Void> result = relay.publish(event);

        kafkaFuture.completeExceptionally(kafkaFailure);

        // Assert: original Kafka failure remains the primary cause.
        assertThatThrownBy(result::join)
                .isInstanceOf(CompletionException.class)
                .hasCause(kafkaFailure);

        verify(stateService, times(1))
                .recordFailedAttempt(
                        event.getId(),
                        event.getClaimToken()
                );

        verify(stateService, never())
                .markPublished(
                        any(UUID.class),
                        any(UUID.class)
                );
    }

    @Test
    void shouldNotRecordKafkaFailureWhenDatabaseUpdateFailsAfterAcknowledgement() {

        // Arrange: Kafka publishes successfully.
        CompletableFuture<SendResult<String, String>> kafkaFuture =
                new CompletableFuture<>();

        when(publisher.publish(event))
                .thenReturn(kafkaFuture);

        // MySQL fails while recording the successful publication.
        RuntimeException databaseFailure =
                new RuntimeException("MySQL unavailable");

        doThrow(databaseFailure)
                .when(stateService)
                .markPublished(
                        event.getId(),
                        event.getClaimToken()
                );

        // Act: start publication.
        CompletableFuture<Void> result = relay.publish(event);

        // Kafka acknowledges successfully.
        kafkaFuture.complete(null);

        // Assert: database failure must propagate.
        assertThat(result).isCompletedExceptionally();
        verify(stateService, times(1))
                .markPublished(
                        event.getId(),
                        event.getClaimToken()
                );

        // This was NOT a failed Kafka publication.
        verify(stateService, never())
                .recordFailedAttempt(
                        any(UUID.class),
                        any(UUID.class)
                );

        verify(publisher, times(1))
                .publish(event);
    }

    @Test
    void shouldNotMarkEventPublishedWhenKafkaAcknowledgementFails() {

        // Arrange: Kafka accepts the send invocation,
        // but later reports publication failure.
        CompletableFuture<SendResult<String, String>> kafkaFuture =
                new CompletableFuture<>();

        when(publisher.publish(event))
                .thenReturn(kafkaFuture);

        // Act: initiate publication.
        CompletableFuture<Void> result = relay.publish(event);

        // Before acknowledgement, publishing must not be marked complete.
        verifyNoInteractions(stateService);

        assertThat(event.getStatus())
                .isEqualTo(OutboxStatus.IN_PROGRESS);

        // Simulate Kafka reporting a failed publication.
        kafkaFuture.completeExceptionally(
                new RuntimeException("Kafka broker unavailable")
        );

        // Assert: the relay observes the failed acknowledgement.
        assertThat(result).isCompletedExceptionally();

        // Kafka failed, so record the failed attempt.
        verify(stateService, times(1))
                .recordFailedAttempt(
                        event.getId(),
                        event.getClaimToken()
                );

        // Kafka failed, so never mark the event published.
        verify(stateService, never())
                .markPublished(
                        any(UUID.class),
                        any(UUID.class)
                );

        // Database event remains eligible for retry.
        assertThat(event.getStatus())
                .isEqualTo(OutboxStatus.IN_PROGRESS);

        assertThat(event.getPublishedAt())
                .isNull();

        verify(publisher, times(1))
                .publish(event);
    }
}
