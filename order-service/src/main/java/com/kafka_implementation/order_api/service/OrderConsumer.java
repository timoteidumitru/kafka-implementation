package com.kafka_implementation.order_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka_implementation.order_api.entity.Order;
import com.kafka_implementation.order_api.repository.OrderRepository;
import com.kafka_implementation.shared_events.PaymentEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OrderConsumer {

    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    public OrderConsumer(OrderRepository orderRepository, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "inventory-update-topic", groupId = "order-service")
    public void handleStockUpdate(ConsumerRecord<String, String> record) {
        log.info("Received stock update message from topic: inventory-update-topic, Key: {}, Partition: {}, Offset: {}",
                record.key(), record.partition(), record.offset());

        try {
            PaymentEvent event = objectMapper.readValue(record.value(), PaymentEvent.class);
            log.info("Deserialized PaymentEvent: {}", event);

            if (event.isApproved()) {
                Order order = new Order(event.getOrderId(), event.getProductCode(),
                        event.getQuantity(), "APPROVED");
                orderRepository.save(order);
                log.info("Order Approved and saved to DB: {}", order);
            } else {
                log.warn("Order rejected due to insufficient stock or payment failure. Order ID: {}",
                        event.getOrderId());
            }
        } catch (Exception e) {
            log.error("Failed to process stock update: {}", e.getMessage(), e);
        }
    }
}
