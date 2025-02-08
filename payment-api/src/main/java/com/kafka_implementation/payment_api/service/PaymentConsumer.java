package com.kafka_implementation.payment_api.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class PaymentConsumer {

    @KafkaListener(topics = "order.placed", groupId = "payment-service")
    public void consumeOrderEvent(String message) {
        System.out.println("Order Event Received: " + message);
        // Validate payment and send result
    }
}
