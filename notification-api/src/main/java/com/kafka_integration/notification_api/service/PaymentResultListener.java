package com.kafka_integration.notification_api.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class PaymentResultListener {

    @KafkaListener(topics = "payment-result-topic", groupId = "notification-service")
    public void consumePaymentResult(String message) {
        System.out.println("Payment Result Received: " + message);
        // Simulate sending a notification
        sendNotification(message);
    }

    private void sendNotification(String message) {
        // Here, you can integrate with email, SMS, or other notification systems
        System.out.println("Notification sent to user: " + message);
    }
}
