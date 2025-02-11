package com.kafka_implementation.order_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka_implementation.order_api.entity.Order;
import com.kafka_implementation.order_api.repository.OrderRepository;
import com.kafka_implementation.shared.dto.PaymentResultEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;


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
        try {
            PaymentResultEvent event = objectMapper.readValue(record.value(), PaymentResultEvent.class);

            if (event.isApproved()) {
                // Save order only if approved
                Order order = new Order(event.getOrderId(), "APPROVED");
                orderRepository.save(order);
            } else {
                System.out.println("❌ Order rejected due to insufficient stock or payment failure.");
            }

        } catch (Exception e) {
            System.err.println("❌ Failed to process stock update: " + e.getMessage());
        }
    }

}