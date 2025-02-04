package com.kafka_implementation.order_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka_implementation.order_api.entity.Order;
import com.kafka_implementation.order_api.repository.OrderRepository;
import com.kafka_implementation.shared.dto.StockUpdateEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class OrderConsumer {

    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    private final List<String> receivedMessages = new CopyOnWriteArrayList<>();

    public OrderConsumer(OrderRepository orderRepository, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "inventory.stock.update", groupId = "order-service")
    public void handleStockUpdate(ConsumerRecord<String, String> record) {
        try {
            StockUpdateEvent event = objectMapper.readValue(record.value(), StockUpdateEvent.class);

            Optional<Order> optionalOrder = orderRepository.findById(event.getOrderId());
            if (optionalOrder.isPresent()) {
                Order order = optionalOrder.get();
                order.setStatus(event.isApproved() ? "APPROVED" : "REJECTED");
                orderRepository.save(order);
            }

            // Store message for testing
            receivedMessages.add(record.value());

        } catch (Exception e) {
            System.err.println("❌ Failed to process stock update: " + e.getMessage());
        }
    }

    // Method for test case to fetch received messages
    public List<String> getReceivedMessages() {
        return new ArrayList<>(receivedMessages);
    }
}
