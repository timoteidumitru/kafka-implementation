package com.kafka_implementation.order_service.producer;

import com.kafka_implementation.shared_events.base.DomainEvent;
import com.kafka_implementation.shared_events.order.OrderCompletedEvent;
import com.kafka_implementation.shared_events.order.OrderCreatedEvent;
import com.kafka_implementation.shared_events.order.OrderFailedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import static com.kafka_implementation.shared_events.topics.Topics.ORDER_EVENTS_V1;

@Component
public class OrderEventPublisher {

    private final KafkaTemplate<String, DomainEvent> kafkaTemplate;

    public OrderEventPublisher(KafkaTemplate<String, DomainEvent> kafkaTemplate) {
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

    private void publish(DomainEvent event) {
        kafkaTemplate.send(
                ORDER_EVENTS_V1,
                event.getAggregateId().toString(),
                event
        );
    }
}