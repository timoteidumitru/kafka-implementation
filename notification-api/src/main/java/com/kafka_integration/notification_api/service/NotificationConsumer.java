package com.kafka_integration.notification_api.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class NotificationConsumer {

    @KafkaListener(topics = "payment-result-topic", groupId = "notification-service")
    public void consumePaymentResult(String message) {
        System.out.println("Notification Event Received: " + message);
        // Send email or SMS notification
    }

}
