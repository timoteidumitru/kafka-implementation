package com.kafka_integration.notification_service.service;

import com.kafka_implementation.shared_events.base.DomainEvent;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    public void notify(DomainEvent event) {
        switch (event.getEventType()) {
            case ORDER_CREATED -> send("📦 Your order has been placed!");
            case PAYMENT_COMPLETED -> send("💳 Payment successful!");
            case PAYMENT_FAILED -> send("❌ Payment failed. Please try again.");
            case INVENTORY_RESERVED -> send("✅ Items reserved. Order confirmed!");
            case INVENTORY_RESERVATION_FAILED -> send("⚠️ Item out of stock.");
            case ORDER_COMPLETED -> send("🎉 Your order is complete!");
            case ORDER_FAILED -> send("🚫 Your order was cancelled.");
            default -> send("ℹ️ Order update received.");
        }
    }

    private void send(String message) {
        System.out.println("[NOTIFICATION]: " + message);
    }
}

