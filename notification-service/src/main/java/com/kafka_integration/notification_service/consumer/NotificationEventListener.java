package com.kafka_integration.notification_service.consumer;

import com.kafka_implementation.shared_events.base.DomainEvent;
import com.kafka_integration.notification_service.config.KafkaTopicsConfig;
import com.kafka_integration.notification_service.service.NotificationService;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventListener {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService service;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public NotificationEventListener(
            NotificationService service,
            KafkaTemplate<String, Object> kafkaTemplate) {
        this.service = service;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
            topics = {
                    "order.events",
                    "payment.events",
                    "inventory.events"
            },
            groupId = "notification-service"
    )
    @Retry(name = "notification-kafka")
    @CircuitBreaker(name = "notification-kafka", fallbackMethod = "fallback")
    @Bulkhead(
            name = "notification-kafka",
            type = Bulkhead.Type.THREADPOOL
    )
    public void onEvent(DomainEvent event) {

        try {
            MDC.put("eventId", event.metadata().eventId().toString());
            MDC.put("correlationId", event.metadata().correlationId().toString());
            MDC.put("aggregateId", event.getAggregateId().toString());
            MDC.put("eventType", event.getEventType().name());

            log.info("Processing notification event");

            service.notify(event);

            log.info("Notification processed successfully");

        } finally {
            MDC.clear();
        }
    }

    /**
     * Retry / CircuitBreaker / Bulkhead fallback
     */
    public void fallback(DomainEvent event, Throwable ex) {

        try {
            MDC.put("eventId", event.metadata().eventId().toString());
            MDC.put("correlationId", event.metadata().correlationId().toString());
            MDC.put("aggregateId", event.getAggregateId().toString());
            MDC.put("eventType", event.getEventType().name());

            log.error(
                    "Notification processing failed — sending event to DLQ",
                    ex
            );

            kafkaTemplate.send(
                    KafkaTopicsConfig.NOTIFICATION_DLQ,
                    event.getAggregateId().toString(),
                    event
            );

        } finally {
            MDC.clear();
        }
    }
}
