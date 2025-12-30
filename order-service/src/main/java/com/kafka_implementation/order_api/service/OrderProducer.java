package com.kafka_implementation.order_api.service;

import com.kafka_implementation.shared_events.OrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class OrderProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OrderProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendOrderEvent(String productCode, int quantity) {
        try {
            long orderId = ThreadLocalRandom.current().nextLong(1, 1000);
            OrderEvent orderEvent = new OrderEvent(orderId, productCode, quantity, "buy");
            String message = objectMapper.writeValueAsString(orderEvent);

            kafkaTemplate.send("order.topic", message);

            log.info("OrderEvent Sent: {}", message);
        } catch (Exception e) {
            log.error("Failed to send order event: {}", e.getMessage(), e);
        }
    }
}
