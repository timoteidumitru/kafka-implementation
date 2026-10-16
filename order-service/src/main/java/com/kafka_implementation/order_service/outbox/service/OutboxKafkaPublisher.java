package com.kafka_implementation.order_service.outbox.service;

import com.kafka_implementation.order_service.outbox.domain.OutboxEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class OutboxKafkaPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxKafkaPublisher(
            @Qualifier("outboxKafkaTemplate")
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<SendResult<String, String>> publish(
            OutboxEvent event
    ) {
        return kafkaTemplate.send(
                event.getTopic(),
                event.getAggregateId().toString(),
                event.getPayload()
        );
    }

}
