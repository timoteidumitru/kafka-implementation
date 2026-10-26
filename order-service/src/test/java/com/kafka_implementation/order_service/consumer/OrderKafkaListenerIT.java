package com.kafka_implementation.order_service.consumer;

import com.kafka_implementation.order_service.config.KafkaConsumerConfig;
import com.kafka_implementation.order_service.service.OrderEventProcessor;
import com.kafka_implementation.shared_events.base.DomainEvent;
import com.kafka_implementation.shared_events.base.EventMetadata;
import com.kafka_implementation.shared_events.inventory.InventoryReservationFailedEvent;
import com.kafka_implementation.shared_events.inventory.InventoryReservedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.junit.jupiter.Container;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.kafka_implementation.shared_events.topics.Topics.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = OrderKafkaListenerIT.TestApplication.class)
class OrderKafkaListenerIT {

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

        registry.add(
                "spring.kafka.listener.concurrency",
                () -> "2"
        );

        registry.add(
                "spring.kafka.listener.poll-timeout",
                () -> "750ms"
        );

        registry.add(
                "spring.kafka.listener.ack-mode",
                () -> "batch"
        );
    }

    @Autowired
    private KafkaTemplate<String, DomainEvent> kafkaTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @MockBean
    private OrderEventProcessor processor;

    @Autowired
    private ConsumerFactory<String, Object> consumerFactory;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldNotConfigureUnrestrictedTrustedPackages() {

        // Read the properties used by the real consumer factory.
        var properties = consumerFactory.getConfigurationProperties();

        String trustedPackages = (String) properties.get(
                JsonDeserializer.TRUSTED_PACKAGES
        );

        assertThat(trustedPackages)
                .as("Trusted packages must be explicitly configured")
                .isNotBlank();

        assertThat(trustedPackages)
                .as("Wildcard package trust must not be enabled")
                .doesNotContain("*");

        assertThat(trustedPackages.split(","))
                .extracting(String::trim)
                .containsExactlyInAnyOrder(
                        "com.kafka_implementation.shared_events.inventory",
                        "com.kafka_implementation.shared_events.payment"
                );
    }

    @Test
    void shouldRejectTypeHeaderOutsideTrustedPackages() {

        // EventMetadata lives in the 'base' package.
        // It is deliberately outside our proposed allowlist.
        EventMetadata unsupportedRootType =
                EventMetadata.create("security-test", 1);

        String topic = INVENTORY_EVENTS_V1;

        var properties = consumerFactory.getConfigurationProperties();

        try (
                JsonSerializer<Object> serializer =
                        new JsonSerializer<>(objectMapper);

                JsonDeserializer<Object> deserializer =
                        new JsonDeserializer<>(objectMapper)
        ) {

            // Configure from the actual consumer factory's
            // property map, not a separately hardcoded allowlist.
            deserializer.configure(properties, false);

            RecordHeaders headers = new RecordHeaders();

            byte[] payload = serializer.serialize(
                    topic,
                    headers,
                    unsupportedRootType
            );

            assertThat(headers.lastHeader("__TypeId__"))
                    .isNotNull();

            assertThatThrownBy(() ->
                    deserializer.deserialize(
                            topic,
                            headers,
                            payload
                    )
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not in the trusted packages");
        }
    }

    @Test
    void shouldApplyListenerSettingsFromSpringProperties() {

        var containers = listenerRegistry.getListenerContainers();

        // Payment and inventory each have one listener,
        // with their own concurrent consumer container.
        assertThat(containers).hasSize(2);

        for (var container : containers) {

            assertThat(container)
                    .isInstanceOf(
                            ConcurrentMessageListenerContainer.class
                    );

            var concurrentContainer =
                    (ConcurrentMessageListenerContainer<?, ?>) container;

            assertThat(concurrentContainer.getConcurrency())
                    .as("Concurrency must come from Spring configuration")
                    .isEqualTo(2);

            assertThat(
                    concurrentContainer
                            .getContainerProperties()
                            .getPollTimeout()
            )
                    .as("Poll timeout must come from Spring configuration")
                    .isEqualTo(750L);

            assertThat(
                    concurrentContainer
                            .getContainerProperties()
                            .getAckMode()
            )
                    .as("Acknowledgement mode must remain BATCH")
                    .isEqualTo(ContainerProperties.AckMode.BATCH);
        }
    }

    @Test
    void shouldRouteBothInventoryEventsFromRealKafka()
            throws Exception {

        UUID orderId = UUID.randomUUID();

        InventoryReservedEvent reserved =
                new InventoryReservedEvent(
                        EventMetadata.create("inventory-service", 1),
                        orderId,
                        UUID.randomUUID(),
                        2
                );

        InventoryReservationFailedEvent failed =
                new InventoryReservationFailedEvent(
                        EventMetadata.create("inventory-service", 1),
                        orderId,
                        "Insufficient stock"
                );

        // Both records use the same topic and partition key.
        kafkaTemplate.send(
                INVENTORY_EVENTS_V1,
                orderId.toString(),
                reserved
        ).get(15, TimeUnit.SECONDS);

        kafkaTemplate.send(
                INVENTORY_EVENTS_V1,
                orderId.toString(),
                failed
        ).get(15, TimeUnit.SECONDS);

        // The actual Kafka listener must invoke both handlers.
        verify(processor, timeout(20000))
                .processInventoryReserved(reserved);

        verify(processor, timeout(20000))
                .processInventoryFailure(failed);

        verifyNoMoreInteractions(processor);
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
            OrderEventListener.class
    })
    static class TestApplication {

        @org.springframework.context.annotation.Bean
        NewTopic inventoryTopic() {
            return TopicBuilder
                    .name(INVENTORY_EVENTS_V1)
                    .partitions(3)
                    .replicas(1)
                    .build();
        }

        @org.springframework.context.annotation.Bean
        NewTopic inventoryDltTopic() {
            return TopicBuilder
                    .name(INVENTORY_EVENTS_V1 + ".DLT")
                    .partitions(3)
                    .replicas(1)
                    .build();
        }

        @org.springframework.context.annotation.Bean
        NewTopic paymentTopic() {
            return TopicBuilder
                    .name(PAYMENT_EVENTS_V1)
                    .partitions(3)
                    .replicas(1)
                    .build();
        }

        @org.springframework.context.annotation.Bean
        NewTopic paymentDltTopic() {
            return TopicBuilder
                    .name(PAYMENT_EVENTS_V1 + ".DLT")
                    .partitions(3)
                    .replicas(1)
                    .build();
        }
    }
}
