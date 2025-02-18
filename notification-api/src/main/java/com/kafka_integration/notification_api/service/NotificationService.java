package com.kafka_integration.notification_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka_implementation.shared.dto.OrderEvent;
import com.kafka_implementation.shared.dto.PaymentEvent;
import com.kafka_implementation.shared.dto.ProductDTO;
import com.kafka_integration.notification_api.entity.Notification;
import com.kafka_integration.notification_api.repository.NotificationRepository;
import com.kafka_integration.notification_api.service.external.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ProductService productService; 
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationRepository notificationRepository, ProductService productService, ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.productService = productService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order.topic", groupId = "notification-service")
    public void handleOrderPlaced(ConsumerRecord<String, String> record) {
        try {
            OrderEvent event = objectMapper.readValue(record.value(), OrderEvent.class);
            List<ProductDTO> availableProducts = productService.getAvailableProducts();
            ProductDTO product = availableProducts.stream()
                    .filter(e -> e.getProductCode().equals(event.getProductCode())).findFirst().orElse(null);

            assert product != null;

            String message = "Order placed: ID " + event.getOrderId() + ", Product: " + product.getName() + " " + product.getDescription();
            saveNotification(message, "ORDER_PLACED");
        } catch (Exception e) {
            log.error("Failed to process order event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "payment-result-topic", groupId = "notification-service")
    public void handlePaymentProcessed(ConsumerRecord<String, String> record) {
        try {
            PaymentEvent event = objectMapper.readValue(record.value(), PaymentEvent.class);
            String message = event.isApproved()
                    ? "Payment successful for Order ID: " + event.getOrderId()
                    : "Payment failed for Order ID: " + event.getOrderId();
            saveNotification(message, "PAYMENT_PROCESSED");
        } catch (Exception e) {
            log.error("Failed to process payment event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "inventory-update-topic", groupId = "notification-service")
    public void handleInventoryUpdate(ConsumerRecord<String, String> record) {
        try {
            PaymentEvent event = objectMapper.readValue(record.value(), PaymentEvent.class);
            String message = "Order ID " + event.getOrderId() + " is now shipping.";
            saveNotification(message, "INVENTORY_UPDATED");
        } catch (Exception e) {
            log.error("Failed to process inventory event: {}", e.getMessage());
        }
    }

    private void saveNotification(String message, String eventType) {
        Notification notification = new Notification(null, message, eventType, LocalDateTime.now());
        notificationRepository.save(notification);
        log.info("Notification saved: {}", message);
    }
}
