package com.kafka_implementation.order_service.consumer;

import com.kafka_implementation.order_service.config.KafkaConsumerConfig;
import com.kafka_implementation.order_service.config.KafkaProducerConfig;
import com.kafka_implementation.order_service.service.OrderEventProcessor;
import com.kafka_implementation.shared_events.base.DomainEvent;
import com.kafka_implementation.shared_events.base.EventMetadata;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.kafka_implementation.shared_events.inventory.InventoryReservedEvent;
import org.springframework.kafka.support.serializer.JsonSerializer;


import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.eq;
import static com.kafka_implementation.shared_events.topics.Topics.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = OrderKafkaDltIT.TestApplication.class)
class OrderKafkaDltIT {

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
    static void configureKafka(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.kafka.bootstrap-servers",
                kafka::getBootstrapServers
        );
        registry.add("spring.kafka.listener.concurrency", () -> "1");
        registry.add("spring.kafka.consumer.auto-offset-reset",
                () -> "earliest");
    }

    @MockBean
    private OrderEventProcessor processor;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void shouldRetryBusinessFailureThenPublishEventToDlt()
            throws Exception {

        UUID orderId = UUID.randomUUID();
        String key = orderId.toString();

        InventoryReservedEvent event = new InventoryReservedEvent(
                EventMetadata.create("inventory-service", 1),
                orderId,
                UUID.randomUUID(),
                2
        );

        // Simulate an unavailable downstream dependency.
        doThrow(new IllegalStateException("Database unavailable"))
                .when(processor)
                .processInventoryReserved(eq(event));

        Map<String, Object> producerConfig = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                JsonSerializer.class
        );

        Map<String, Object> consumerConfig = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "business-dlt-inspector-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ByteArrayDeserializer.class
        );

        try (
                KafkaProducer<String, DomainEvent> producer =
                        new KafkaProducer<>(producerConfig);

                KafkaConsumer<String, byte[]> dltConsumer =
                        new KafkaConsumer<>(consumerConfig)
        ) {

            TopicPartition dltPartition =
                    new TopicPartition(DLT_TOPIC, 0);

            dltConsumer.assign(List.of(dltPartition));
            dltConsumer.seekToBeginning(List.of(dltPartition));

            // JsonSerializer will add the type header.
            ProducerRecord<String, DomainEvent> originalRecord =
                    new ProducerRecord<>(
                            INVENTORY_EVENTS_V1,
                            0,
                            key,
                            event
                    );

            var originalMetadata = producer.send(originalRecord)
                    .get(15, TimeUnit.SECONDS);

            ConsumerRecord<String, byte[]> recovered = null;

            long deadline = System.nanoTime()
                    + Duration.ofSeconds(30).toNanos();

            while (System.nanoTime() < deadline && recovered == null) {
                var records = dltConsumer.poll(Duration.ofSeconds(1));

                for (ConsumerRecord<String, byte[]> record : records) {
                    if (key.equals(record.key())) {
                        recovered = record;
                        break;
                    }
                }
            }

            assertThat(recovered)
                    .as("Business failure should reach DLT")
                    .isNotNull();

            assertThat(recovered.key()).isEqualTo(key);

            InventoryReservedEvent restoredEvent = mapper.readValue(
                    recovered.value(),
                    InventoryReservedEvent.class
            );

            assertThat(restoredEvent)
                    .as("Recovered DLT event should equal the original domain event")
                    .isEqualTo(event);

            // FixedBackOff(1000L, 3L) means:
            // 1 original attempt + 3 retries.
            verify(processor, times(4))
                    .processInventoryReserved(eq(event));

            assertThat(recovered.partition())
                    .isEqualTo(originalMetadata.partition());
        }
    }

    @Test
    void shouldPreserveOriginalBytesInDltAfterDeserializationFailure()
            throws Exception {

        byte[] invalidPayload =
                "{invalid-json".getBytes(StandardCharsets.UTF_8);

        String key = UUID.randomUUID().toString();

        Map<String, Object> producerConfig = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                ByteArraySerializer.class
        );

        Map<String, Object> consumerConfig = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "dlt-inspector-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ByteArrayDeserializer.class
        );

        try (
                KafkaProducer<String, byte[]> producer =
                        new KafkaProducer<>(producerConfig);

                KafkaConsumer<String, byte[]> dltConsumer =
                        new KafkaConsumer<>(consumerConfig)
        ) {

            // Read the DLT as bytes, independently of Spring Kafka.
            TopicPartition dltPartition =
                    new TopicPartition(DLT_TOPIC, 0);

            dltConsumer.assign(List.of(dltPartition));
            dltConsumer.seekToBeginning(List.of(dltPartition));

            // Publish invalid JSON with a valid, trusted type header.
            // This makes JSON parsing fail inside the real consumer.
            ProducerRecord<String, byte[]> invalidRecord =
                    new ProducerRecord<>(
                            INVENTORY_EVENTS_V1,
                            0,
                            key,
                            invalidPayload
                    );

            invalidRecord.headers().add(new RecordHeader(
                    "__TypeId__",
                    "com.kafka_implementation.shared_events.inventory.InventoryReservedEvent"
                            .getBytes(StandardCharsets.UTF_8)
            ));

            var originalMetadata = producer.send(invalidRecord)
                    .get(15, TimeUnit.SECONDS);

            ConsumerRecord<String, byte[]> recovered = null;
            long deadline = System.nanoTime()
                    + Duration.ofSeconds(25).toNanos();

            while (System.nanoTime() < deadline && recovered == null) {
                ConsumerRecords<String, byte[]> records =
                        dltConsumer.poll(Duration.ofSeconds(1));

                for (ConsumerRecord<String, byte[]> record : records) {
                    if (key.equals(record.key())) {
                        recovered = record;
                        break;
                    }
                }
            }


            assertThat(recovered)
                    .as("Invalid message should arrive in the DLT")
                    .isNotNull();

            // 1. Verify the original payload and key were preserved.
            assertThat(recovered.value())
                    .as("Original bytes must survive unchanged")
                    .containsExactly(invalidPayload);

            assertThat(recovered.key())
                    .isEqualTo(key);

            // 2. Extract the original record metadata from DLT headers.
            var originalTopicHeader = recovered.headers().lastHeader(
                    KafkaHeaders.DLT_ORIGINAL_TOPIC
            );

            var originalPartitionHeader = recovered.headers().lastHeader(
                    KafkaHeaders.DLT_ORIGINAL_PARTITION
            );

            var originalOffsetHeader = recovered.headers().lastHeader(
                    KafkaHeaders.DLT_ORIGINAL_OFFSET
            );

            // 3. Verify the required headers exist.
            assertThat(originalTopicHeader).isNotNull();
            assertThat(originalPartitionHeader).isNotNull();
            assertThat(originalOffsetHeader).isNotNull();

            // 4. Compare the original topic.
            assertThat(new String(
                    originalTopicHeader.value(),
                    StandardCharsets.UTF_8
            )).isEqualTo(INVENTORY_EVENTS_V1);

            // 5. Compare the original partition.
            assertThat(
                    java.nio.ByteBuffer.wrap(originalPartitionHeader.value()).getInt()
            ).isEqualTo(originalMetadata.partition());

            // 6. Compare the original offset.
            assertThat(
                    java.nio.ByteBuffer.wrap(originalOffsetHeader.value()).getLong()
            ).isEqualTo(originalMetadata.offset());

            // 7. Verify the DLT uses the original partition.
            assertThat(recovered.partition())
                    .isEqualTo(originalMetadata.partition());

            // 8. Invalid JSON must never reach business processing.
            verifyNoInteractions(processor);

        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class
    })
    @EnableKafka
    @Import({
            KafkaConsumerConfig.class,
            KafkaProducerConfig.class,
            OrderEventListener.class
    })
    static class TestApplication {

        @org.springframework.context.annotation.Bean
        NewTopic inventoryTopic() {
            return TopicBuilder.name(INVENTORY_EVENTS_V1)
                    .partitions(3).replicas(1).build();
        }

        @org.springframework.context.annotation.Bean
        NewTopic inventoryDltTopic() {
            return TopicBuilder.name(DLT_TOPIC)
                    .partitions(3).replicas(1).build();
        }

        @org.springframework.context.annotation.Bean
        NewTopic paymentTopic() {
            return TopicBuilder.name(PAYMENT_EVENTS_V1)
                    .partitions(3).replicas(1).build();
        }

        @org.springframework.context.annotation.Bean
        NewTopic paymentDltTopic() {
            return TopicBuilder.name(PAYMENT_EVENTS_V1 + ".DLT")
                    .partitions(3).replicas(1).build();
        }
    }
}
