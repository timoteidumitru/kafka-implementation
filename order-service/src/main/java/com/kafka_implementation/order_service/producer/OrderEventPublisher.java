package com.kafka_implementation.order_service.producer;

import com.kafka_implementation.order_service.config.KafkaTopicsConfig;
import com.kafka_implementation.shared_events.order.OrderCompletedEvent;
import com.kafka_implementation.shared_events.order.OrderCreatedEvent;
import com.kafka_implementation.shared_events.order.OrderFailedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderCreated(OrderCreatedEvent event) {
        kafkaTemplate.send(
                KafkaTopicsConfig.ORDER_EVENTS,
                event.getAggregateId().toString(),
                event
        );
    }

    public void publishOrderCompleted(OrderCompletedEvent event) {
        kafkaTemplate.send(
                KafkaTopicsConfig.ORDER_EVENTS,
                event.getAggregateId().toString(),
                event
        );
    }

    public void publishOrderFailed(OrderFailedEvent event) {
        kafkaTemplate.send(
                KafkaTopicsConfig.ORDER_EVENTS,
                event.getAggregateId().toString(),
                event
        );
    }
}
