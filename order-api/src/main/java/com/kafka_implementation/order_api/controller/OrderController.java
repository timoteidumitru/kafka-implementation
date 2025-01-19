package com.kafka_implementation.order_api.controller;

import com.kafka_implementation.order_api.entity.Order;
import com.kafka_implementation.order_api.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private static final String ORDER_TOPIC = "order-topic";

    @PostMapping
    public String createOrder(@RequestBody Order order) {
        order.setStatus("PENDING");
        Order savedOrder = orderRepository.save(order);

        // Publish event to Kafka
        kafkaTemplate.send(ORDER_TOPIC, "Order created with ID: " + savedOrder.getId());
        return "Order created successfully!";
    }

    @GetMapping("/{id}")
    public Order getOrder(@PathVariable Long id) {
        return orderRepository.findById(id).orElse(null);
    }
}

