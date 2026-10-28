package com.kafka_implementation.order_service.config;

import com.kafka_implementation.order_service.consumer.UnsupportedInventoryEventException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class KafkaDltRecoveryTest {

    @Test
    void shouldSendUnsupportedInventoryEventToDltWithoutRetries() {

        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> dltTemplate =
                mock(KafkaTemplate.class);

        @SuppressWarnings("unchecked")
        SendResult<String, Object> sendResult =
                mock(SendResult.class);

        when(dltTemplate.send(any(ProducerRecord.class)))
                .thenReturn(
                        CompletableFuture.completedFuture(sendResult)
                );

        DefaultErrorHandler handler =
                new KafkaConsumerConfig().errorHandler(dltTemplate);

        ConsumerRecord<String, Object> record =
                new ConsumerRecord<>(
                        "inventory.events.v1",
                        0,
                        42L,
                        "order-42",
                        "unsupported-event"
                );

        @SuppressWarnings("unchecked")
        Consumer<String, Object> consumer =
                mock(Consumer.class);

        MessageListenerContainer container =
                mock(MessageListenerContainer.class);

        when(consumer.partitionsFor(
                eq("inventory.events.v1.DLT"),
                any(Duration.class)
        )).thenReturn(List.of(
                new PartitionInfo(
                        "inventory.events.v1.DLT",
                        0,
                        null,
                        null,
                        null
                )
        ));

        RuntimeException failure =
                new UnsupportedInventoryEventException(
                        "Unsupported inventory event type"
                );

        // A permanent failure should immediately enter recovery.
        boolean recovered = handler.handleOne(
                failure,
                record,
                consumer,
                container
        );

        assertThat(recovered)
                .as("Unsupported event should recover without retries")
                .isTrue();

        verify(dltTemplate, times(1))
                .send(any(ProducerRecord.class));
    }

    @Test
    void shouldRetryOrdinaryIllegalArgumentExceptionBeforeDlt() {

        // ARRANGE: mock a successful DLT publication.
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> dltTemplate =
                mock(KafkaTemplate.class);

        @SuppressWarnings("unchecked")
        SendResult<String, Object> sendResult =
                mock(SendResult.class);

        when(dltTemplate.send(any(ProducerRecord.class)))
                .thenReturn(
                        CompletableFuture.completedFuture(sendResult)
                );

        // Use the actual production error-handler configuration.
        DefaultErrorHandler handler =
                new KafkaConsumerConfig().errorHandler(dltTemplate);

        ConsumerRecord<String, Object> record =
                new ConsumerRecord<>(
                        "inventory.events.v1",
                        0,
                        42L,
                        "order-42",
                        "unsupported-event"
                );

        @SuppressWarnings("unchecked")
        Consumer<String, Object> consumer =
                mock(Consumer.class);

        MessageListenerContainer container =
                mock(MessageListenerContainer.class);

        when(consumer.partitionsFor(
                eq("inventory.events.v1.DLT"),
                any(Duration.class)
        )).thenReturn(List.of(
                new PartitionInfo(
                        "inventory.events.v1.DLT",
                        0,
                        null,
                        null,
                        null
                )
        ));

        // Simulate the exception currently thrown
        // by OrderEventListener for an unsupported event.
        IllegalArgumentException unsupportedEvent =
                new IllegalArgumentException(
                        "Unsupported inventory event type"
                );

        // ACT + ASSERT: the first three failures remain retryable.
        for (int attempt = 1; attempt <= 3; attempt++) {

            boolean recovered = handler.handleOne(
                    unsupportedEvent,
                    record,
                    consumer,
                    container
            );

            assertThat(recovered)
                    .as("Attempt %s should remain unrecovered", attempt)
                    .isFalse();
        }

        // No DLT publication should have occurred yet.
        verify(dltTemplate, never())
                .send(any(ProducerRecord.class));

        // Fourth failure: the retry budget is exhausted.
        boolean recovered = handler.handleOne(
                unsupportedEvent,
                record,
                consumer,
                container
        );

        assertThat(recovered)
                .as("Fourth failure should trigger DLT recovery")
                .isTrue();

        verify(dltTemplate, times(1))
                .send(any(ProducerRecord.class));
    }

    @Test
    void shouldRetryRecoveryAfterDltPublicationFailsThenSucceeds() {

        // ARRANGE
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> dltTemplate =
                mock(KafkaTemplate.class);

        CompletableFuture<SendResult<String, Object>> failedSend =
                CompletableFuture.failedFuture(
                        new IllegalStateException(
                                "DLT broker temporarily unavailable"
                        )
                );

        @SuppressWarnings("unchecked")
        SendResult<String, Object> sendResult =
                mock(SendResult.class);

        CompletableFuture<SendResult<String, Object>> successfulSend =
                CompletableFuture.completedFuture(sendResult);

        // First DLT publication fails; second succeeds.
        when(dltTemplate.send(any(ProducerRecord.class)))
                .thenReturn(failedSend, successfulSend);

        DefaultErrorHandler handler =
                new KafkaConsumerConfig().errorHandler(dltTemplate);

        ConsumerRecord<String, Object> record =
                new ConsumerRecord<>(
                        "inventory.events.v1",
                        0,
                        42L,
                        "order-42",
                        "failed-event"
                );

        @SuppressWarnings("unchecked")
        Consumer<String, Object> consumer = mock(Consumer.class);

        MessageListenerContainer container =
                mock(MessageListenerContainer.class);

        when(consumer.partitionsFor(
                eq("inventory.events.v1.DLT"),
                any(Duration.class)
        )).thenReturn(List.of(
                new PartitionInfo(
                        "inventory.events.v1.DLT",
                        0,
                        null,
                        null,
                        null
                )
        ));

        RuntimeException processingFailure =
                new IllegalStateException("Database unavailable");

        // ACT 1: exhaust the initial retry budget.
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(handler.handleOne(
                    processingFailure,
                    record,
                    consumer,
                    container
            ))
                    .as("Attempt %s should remain unrecovered", attempt)
                    .isFalse();
        }

        verify(dltTemplate, never())
                .send(any(ProducerRecord.class));

        // Attempt 4: the first DLT publication fails.
        boolean firstRecovery = handler.handleOne(
                processingFailure,
                record,
                consumer,
                container
        );

        assertThat(firstRecovery)
                .as("Failed DLT publication must not recover the record")
                .isFalse();

        verify(dltTemplate, times(1))
                .send(any(ProducerRecord.class));

        // ACT 2: Spring Kafka resets the backoff after recovery fails.
        for (int attempt = 5; attempt <= 7; attempt++) {
            assertThat(handler.handleOne(
                    processingFailure,
                    record,
                    consumer,
                    container
            ))
                    .as("Attempt %s should still be unrecovered", attempt)
                    .isFalse();
        }

        // The second DLT publication has not happened yet.
        verify(dltTemplate, times(1))
                .send(any(ProducerRecord.class));

        // Attempt 8: Kafka acknowledges the second DLT publication.
        boolean secondRecovery = handler.handleOne(
                processingFailure,
                record,
                consumer,
                container
        );

        // ASSERT
        assertThat(secondRecovery)
                .as("Recovery must succeed after DLT publication succeeds")
                .isTrue();

        verify(dltTemplate, times(2))
                .send(any(ProducerRecord.class));
    }

    @Test
    void shouldNotRecoverRecordWhenDltPublicationFails() {

        // ARRANGE: simulate an asynchronous Kafka publication failure.
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> dltTemplate =
                mock(KafkaTemplate.class);

        CompletableFuture<SendResult<String, Object>> failedSend =
                CompletableFuture.failedFuture(
                        new IllegalStateException(
                                "Simulated DLT broker failure"
                        )
                );

        when(dltTemplate.send(
                any(ProducerRecord.class)
        )).thenReturn(failedSend);

        // Build the real error handler using production configuration.
        DefaultErrorHandler handler =
                new KafkaConsumerConfig().errorHandler(dltTemplate);

        ConsumerRecord<String, Object> record =
                new ConsumerRecord<>(
                        "inventory.events.v1",
                        0,
                        42L,
                        "order-42",
                        "failed-event"
                );

        @SuppressWarnings("unchecked")
        Consumer<String, Object> consumer = mock(Consumer.class);

        MessageListenerContainer container =
                mock(MessageListenerContainer.class);

        // The recoverer verifies that the DLT partition exists.
        when(consumer.partitionsFor(
                eq("inventory.events.v1.DLT"),
                any(Duration.class)
        )).thenReturn(List.of(
                new PartitionInfo(
                        "inventory.events.v1.DLT",
                        0,
                        null,
                        null,
                        null
                )
        ));

        RuntimeException processingFailure =
                new IllegalStateException("Database unavailable");

        // ACT: the first 3 calls should not recover the record.
        for (int attempt = 1; attempt <= 3; attempt++) {

            boolean recovered = handler.handleOne(
                    processingFailure,
                    record,
                    consumer,
                    container
            );

            assertThat(recovered)
                    .as("Attempt %s must not mark recovery complete",
                            attempt)
                    .isFalse();
        }

        // The fourth failure exhausts the configured backoff.
        // The recoverer now attempts DLT publication, which fails.
        boolean recovered = handler.handleOne(
                processingFailure,
                record,
                consumer,
                container
        );

        // ASSERT: failed DLT publication is not successful recovery.
        assertThat(recovered)
                .as("A failed DLT send must not recover the record")
                .isFalse();

        // Confirm that the recovery path actually sent a DLT record.
        verify(dltTemplate, times(1))
                .send(any(ProducerRecord.class));
    }
}
