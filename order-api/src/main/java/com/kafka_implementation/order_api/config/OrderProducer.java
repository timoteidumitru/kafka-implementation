package com.kafka_implementation.order_api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.*;

@Configuration
public class OrderProducer {
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${order.topic.name:order-topic}")
    private String orderTopic;

    public OrderProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendOrderEvent(String message) {
        kafkaTemplate.send(orderTopic, message);
        System.out.println("Order Event Sent: " + message);
    }
}

