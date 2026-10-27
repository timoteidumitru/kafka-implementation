package com.kafka_implementation.order_service.consumer;

import com.kafka_implementation.order_service.service.OrderEventProcessor;
import com.kafka_implementation.shared_events.base.DomainEvent;
import com.kafka_implementation.shared_events.base.EventMetadata;
import com.kafka_implementation.shared_events.inventory.InventoryReservedEvent;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.kafka_implementation.shared_events.topics.Topics.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = OrderKafkaDltIT.TestApplication.class)
class OrderKafkaDltOffsetSafetyIT {

    // Must match the actual Order Service listener group.
    private static final String GROUP_ID = "order-service";

    private static final String DLT_TOPIC =
            INVENTORY_EVENTS_V1 + ".DLT";

    @Container
    static final ConfluentKafkaContainer kafka =
            new ConfluentKafkaContainer(
                    DockerImageName.parse(
                            "confluentinc/cp-kafka:7.4.1"
                    )
            );

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {

        registry.add(
                "spring.kafka.bootstrap-servers",
                kafka::getBootstrapServers
        );

        registry.add(
                "spring.kafka.consumer.auto-offset-reset",
                () -> "earliest"
        );

        registry.add(
                "spring.kafka.listener.concurrency",
                () -> "1"
        );

        registry.add(
                "spring.kafka.listener.ack-mode",
                () -> "batch"
        );
    }

    @MockBean
    private OrderEventProcessor processor;

    @SpyBean(name = "dltKafkaTemplate")
    private KafkaTemplate<String, Object> dltTemplate;

    @Test
    void shouldNotCommitPastFailedRecordUntilDltPublishSucceeds()
            throws Exception {

        // ARRANGE
        UUID orderId = UUID.randomUUID();
        String key = orderId.toString();

        InventoryReservedEvent event =
                new InventoryReservedEvent(
                        EventMetadata.create("inventory-service", 1),
                        orderId,
                        UUID.randomUUID(),
                        2
                );

        doThrow(new IllegalStateException("Database unavailable"))
                .when(processor)
                .processInventoryReserved(eq(event));

        AtomicInteger dltAttempts = new AtomicInteger();

        CountDownLatch secondDltAttempt =
                new CountDownLatch(1);

        CountDownLatch allowSuccessfulDlt =
                new CountDownLatch(1);

        // First DLT send fails.
        // Second DLT send is paused until we inspect the offset.
        doAnswer(invocation -> {

            int attempt = dltAttempts.incrementAndGet();

            if (attempt == 1) {
                return CompletableFuture
                        .<SendResult<String, Object>>failedFuture(
                                new IllegalStateException(
                                        "Simulated DLT send failure"
                                )
                        );
            }

            if (attempt == 2) {

                secondDltAttempt.countDown();

                if (!allowSuccessfulDlt.await(
                        30, TimeUnit.SECONDS
                )) {
                    throw new IllegalStateException(
                            "Timed out waiting to release DLT send"
                    );
                }
            }

            return invocation.callRealMethod();

        }).when(dltTemplate).send(any(ProducerRecord.class));

        Map<String, Object> producerConfig = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers(),

                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class,

                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                JsonSerializer.class
        );

        Map<String, Object> inspectorConfig = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers(),

                // Query the same committed offsets as Order Service.
                ConsumerConfig.GROUP_ID_CONFIG,
                GROUP_ID,

                // Inspector must never commit its own offsets.
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false,

                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,

                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ByteArrayDeserializer.class,

                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );

        TopicPartition sourcePartition =
                new TopicPartition(INVENTORY_EVENTS_V1, 0);

        TopicPartition dltPartition =
                new TopicPartition(DLT_TOPIC, 0);

        try (
                KafkaProducer<String, DomainEvent> producer =
                        new KafkaProducer<>(producerConfig);

                KafkaConsumer<String, byte[]> inspector =
                        new KafkaConsumer<>(inspectorConfig)
        ) {

            ProducerRecord<String, DomainEvent> original =
                    new ProducerRecord<>(
                            INVENTORY_EVENTS_V1,
                            0,
                            key,
                            event
                    );

            RecordMetadata metadata =
                    producer.send(original)
                            .get(15, TimeUnit.SECONDS);

            long originalOffset = metadata.offset();

            // ACT 1: Observe offset while recovery is incomplete.
            try {
                assertThat(secondDltAttempt.await(
                        25, TimeUnit.SECONDS
                ))
                        .as("Recovery must retry DLT publication")
                        .isTrue();

                assertThat(dltAttempts.get())
                        .isEqualTo(2);

                OffsetAndMetadata current =
                        inspector.committed(
                                Set.of(sourcePartition),
                                Duration.ofSeconds(5)
                        ).get(sourcePartition);

                // Kafka commits the NEXT offset to read.
                assertThat(
                        current == null
                                || current.offset() <= originalOffset
                )
                        .as(
                                "Unsafe offset advance. " +
                                        "Failed record=%s, committed=%s",
                                originalOffset,
                                current
                        )
                        .isTrue();

            } finally {
                // Always release the listener thread.
                allowSuccessfulDlt.countDown();
            }

            // ACT 2: Verify actual DLT publication.
            inspector.assign(List.of(dltPartition));
            inspector.seekToBeginning(List.of(dltPartition));

            ConsumerRecord<String, byte[]> recovered = null;

            long deadline = System.nanoTime()
                    + Duration.ofSeconds(30).toNanos();

            while (System.nanoTime() < deadline
                    && recovered == null) {

                ConsumerRecords<String, byte[]> records =
                        inspector.poll(Duration.ofMillis(500));

                for (ConsumerRecord<String, byte[]> record : records) {

                    if (key.equals(record.key())) {
                        recovered = record;
                        break;
                    }
                }
            }

            assertThat(recovered)
                    .as("Event must eventually reach the DLT")
                    .isNotNull();

            assertThat(recovered.key())
                    .isEqualTo(key);

            // ASSERT: After successful recovery, source offset advances.
            OffsetAndMetadata finalOffset = null;

            deadline = System.nanoTime()
                    + Duration.ofSeconds(20).toNanos();

            while (System.nanoTime() < deadline) {

                finalOffset = inspector.committed(
                        Set.of(sourcePartition),
                        Duration.ofSeconds(5)
                ).get(sourcePartition);

                if (finalOffset != null
                        && finalOffset.offset() > originalOffset) {
                    break;
                }

                Thread.sleep(200);
            }

            assertThat(finalOffset)
                    .as(
                            "Order Service must commit after " +
                                    "successful DLT recovery"
                    )
                    .isNotNull();

            assertThat(finalOffset.offset())
                    .as(
                            "Committed offset must advance " +
                                    "past original offset %s",
                            originalOffset
                    )
                    .isGreaterThan(originalOffset);

            assertThat(dltAttempts.get())
                    .isEqualTo(2);

            verify(processor, atLeast(2))
                    .processInventoryReserved(eq(event));
        }
    }
}
