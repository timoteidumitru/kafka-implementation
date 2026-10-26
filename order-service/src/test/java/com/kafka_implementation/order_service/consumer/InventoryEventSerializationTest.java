package com.kafka_implementation.order_service.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka_implementation.shared_events.base.DomainEvent;
import com.kafka_implementation.shared_events.base.EventMetadata;
import com.kafka_implementation.shared_events.inventory.InventoryReservedEvent;
import com.kafka_implementation.shared_events.inventory.InventoryReservationFailedEvent;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryEventSerializationTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldDeserializeReservedEventIntoConcreteRecord() throws Exception {
        InventoryReservedEvent event = new InventoryReservedEvent(
                EventMetadata.create("inventory-service", 1),
                UUID.randomUUID(),
                UUID.randomUUID(),
                2
        );

        assertRoundTrip(event);
    }

    @Test
    void shouldDeserializeFailedEventIntoConcreteRecord() throws Exception {
        InventoryReservationFailedEvent event =
                new InventoryReservationFailedEvent(
                        EventMetadata.create("inventory-service", 1),
                        UUID.randomUUID(),
                        "Insufficient stock"
                );

        assertRoundTrip(event);
    }

    private void assertRoundTrip(DomainEvent original) throws Exception {

        try (
                JsonSerializer<DomainEvent> serializer =
                        new JsonSerializer<>(objectMapper);

                JsonDeserializer<Object> deserializer =
                        new JsonDeserializer<>(objectMapper)
        ) {
            deserializer.addTrustedPackages(
                    "com.kafka_implementation.shared_events.inventory"
            );

            String topic = "inventory.events.v1";
            RecordHeaders headers = new RecordHeaders();

            byte[] payload = serializer.serialize(
                    topic,
                    headers,
                    original
            );

            String json = new String(
                    payload,
                    java.nio.charset.StandardCharsets.UTF_8
            );

            com.fasterxml.jackson.databind.JsonNode root =
                    objectMapper.readTree(json);

            assertThat(root.has("version")).isFalse();
            assertThat(root.has("eventType")).isFalse();
            assertThat(root.has("aggregateId")).isFalse();

            assertThat(root.path("metadata").path("version").asInt())
                    .isEqualTo(original.getVersion());

            assertThat(headers.lastHeader("__TypeId__"))
                    .isNotNull();

            Object restored = deserializer.deserialize(
                    topic,
                    headers,
                    payload
            );

            assertThat(restored)
                    .isInstanceOf(original.getClass())
                    .isEqualTo(original);
        }
    }
}
