package com.kafka_integration.notification_api.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class NotificationConsumer {

    @KafkaListener(topics = "notification.topic", groupId = "notification-service")
    public void consumeNotification(String message) {
        System.out.println("Notification Received: " + message);
        // Send email or SMS notification
    }
}
