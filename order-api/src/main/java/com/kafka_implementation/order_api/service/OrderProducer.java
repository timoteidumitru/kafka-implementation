package com.kafka_implementation.order_api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderProducer {
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${order.topic.name:order-topic}")
    private String orderTopic;

    public OrderProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendOrderEvent(Long orderId) {
        String message = "New Order Created: " + orderId;
        kafkaTemplate.send(orderTopic, message);
        System.out.println("Order Event Sent: " + message);
    }
}
