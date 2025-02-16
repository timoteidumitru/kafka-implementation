package com.kafka_implementation.inventory_api.service;

import com.kafka_implementation.shared.dto.PaymentEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class InventoryProducer {

    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    public InventoryProducer(KafkaTemplate<String, PaymentEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendStockUpdate(PaymentEvent event) {
        log.info("📤 Sending Stock Update Event: {}", event);

        try {
            kafkaTemplate.send("inventory-update-topic", event);
            log.info("✅ Stock update event successfully sent to topic: inventory-update-topic");
        } catch (Exception e) {
            log.error("🚨 Failed to send stock update event: {}", e.getMessage(), e);
        }
    }
}
