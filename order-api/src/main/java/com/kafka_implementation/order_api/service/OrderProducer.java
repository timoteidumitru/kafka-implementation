package com.kafka_implementation.order_api.service;

import com.kafka_implementation.order_api.entity.Order;
import com.kafka_implementation.shared.dto.OrderPlacedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OrderProducer {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${order.topic.name:order.topic}")
    private String orderTopic;

    public OrderProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendOrderEvent(Order order) {
        try {
            OrderPlacedEvent orderEvent = new OrderPlacedEvent(
                    order.getId(), order.getProductCode(), order.getQuantity()
            );
            String message = objectMapper.writeValueAsString(orderEvent);

            kafkaTemplate.send(orderTopic, message);
            System.out.println("✅ OrderPlacedEvent Sent: " + message);
        } catch (Exception e) {
            System.err.println("❌ Failed to send order event: " + e.getMessage());
        }
    }
}
