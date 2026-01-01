package com.kafka_integration.notification_service.consumer;

import com.kafka_implementation.shared_events.base.DomainEvent;
import com.kafka_integration.notification_service.service.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventListener {

    private final NotificationService notificationService;

    public NotificationEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "order.events", groupId = "notification-service")
    public void onOrderEvents(DomainEvent event) {
        notificationService.notify(event);
    }

    @KafkaListener(topics = "payment.events", groupId = "notification-service")
    public void onPaymentEvents(DomainEvent event) {
        notificationService.notify(event);
    }

    @KafkaListener(topics = "inventory.events", groupId = "notification-service")
    public void onInventoryEvents(DomainEvent event) {
        notificationService.notify(event);
    }
}

