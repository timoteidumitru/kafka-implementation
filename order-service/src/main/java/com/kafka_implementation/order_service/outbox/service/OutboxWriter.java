package com.kafka_implementation.order_service.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.kafka_implementation.order_service.outbox.domain.OutboxEvent;
import com.kafka_implementation.order_service.outbox.repository.OutboxEventRepository;

import com.kafka_implementation.shared_events.base.DomainEvent;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OutboxWriter {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxWriter(
            OutboxEventRepository repository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void store(
            UUID eventId,
            String topic,
            DomainEvent event
    ) {
        try {
            String payload = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = new OutboxEvent(
                    eventId,
                    event.getAggregateId(),
                    topic,
                    event.getEventType().name(),
                    payload
            );

            repository.save(outboxEvent);

        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Unable to serialize outgoing event: " + eventId,
                    ex
            );
        }
    }
}
