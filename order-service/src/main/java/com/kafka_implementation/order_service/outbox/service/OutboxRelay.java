package com.kafka_implementation.order_service.outbox.service;

import com.kafka_implementation.order_service.outbox.domain.OutboxEvent;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class OutboxRelay {

    private final OutboxKafkaPublisher publisher;
    private final OutboxPublicationStateService stateService;

    public OutboxRelay(
            OutboxKafkaPublisher publisher,
            OutboxPublicationStateService stateService
    ) {
        this.publisher = publisher;
        this.stateService = stateService;
    }

    public CompletableFuture<Void> publish(OutboxEvent event) {

        return publisher.publish(event)

                // Handles failure of Kafka publication only.
                .whenComplete((sendResult, kafkaFailure) -> {
                    if (kafkaFailure != null) {
                        stateService.recordFailedAttempt(event.getId());
                    }
                })

                // Runs only if Kafka and the previous stage succeeded.
                .thenAccept(sendResult ->
                        stateService.markPublished(event.getId())
                );
    }

}
