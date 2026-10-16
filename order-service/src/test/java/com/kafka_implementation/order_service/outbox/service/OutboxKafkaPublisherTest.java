package com.kafka_implementation.order_service.outbox.service;

import com.kafka_implementation.order_service.outbox.domain.OutboxEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxKafkaPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OutboxKafkaPublisher publisher;

    @Test
    void shouldPublishStoredJsonWithoutReserializing() {

        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        String payload = """
                {"eventType":"ORDER_CREATED","orderId":"%s"}
                """.formatted(orderId).trim();

        OutboxEvent event = new OutboxEvent(
                eventId,
                orderId,
                "order.events.v1",
                "ORDER_CREATED",
                payload
        );

        CompletableFuture<SendResult<String, String>> kafkaFuture =
                new CompletableFuture<>();

        when(kafkaTemplate.send(
                event.getTopic(),
                orderId.toString(),
                payload
        )).thenReturn(kafkaFuture);

        CompletableFuture<SendResult<String, String>> result =
                publisher.publish(event);

        verify(kafkaTemplate).send(
                "order.events.v1",
                orderId.toString(),
                payload
        );

        assertThat(result).isSameAs(kafkaFuture);

        assertThat(event.getStatus().name())
                .isEqualTo("PENDING");

        assertThat(event.getPublishedAt()).isNull();
    }
}
