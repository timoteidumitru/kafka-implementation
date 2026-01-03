package com.kafka_implementation.order_service.producer;

import com.kafka_implementation.order_service.config.KafkaTopicsConfig;
import com.kafka_implementation.shared_events.base.DomainEvent;
import com.kafka_implementation.shared_events.order.OrderCompletedEvent;
import com.kafka_implementation.shared_events.order.OrderCreatedEvent;
import com.kafka_implementation.shared_events.order.OrderFailedEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderCreated(OrderCreatedEvent event) {
        publish(event);
    }

    public void publishOrderCompleted(OrderCompletedEvent event) {
        publish(event);
    }

    public void publishOrderFailed(OrderFailedEvent event) {
        publish(event);
    }

    // 🔐 Single protected Kafka boundary
    @CircuitBreaker(name = "order-kafka", fallbackMethod = "fallback")
    @Retry(name = "order-kafka")
    private void publish(DomainEvent event) {
        kafkaTemplate.send(
                KafkaTopicsConfig.ORDER_EVENTS,
                event.getAggregateId().toString(),
                event
        );
    }

    // 🛟 Safe fallback
    private void fallback(DomainEvent event, Throwable ex) {
        System.err.println(
                "[ORDER-SERVICE] Failed to publish event " +
                        event.getEventType() +
                        " for aggregate " + event.getAggregateId() +
                        " → " + ex.getMessage()
        );

        // Phase 7: Outbox pattern will go here
    }
}
