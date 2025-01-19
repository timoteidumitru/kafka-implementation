package com.kafka_implementation.order_api.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class OrderEventListener {

    @KafkaListener(topics = "payment-result-topic", groupId = "order-service")
    public void consumePaymentEvent(String message) {
        System.out.println("Payment Event Received: " + message);
        // Update order status based on payment result
    }
}

