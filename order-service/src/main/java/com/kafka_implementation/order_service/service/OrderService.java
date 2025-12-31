package com.kafka_implementation.order_service.service;

import com.kafka_implementation.order_service.domain.Order;
import com.kafka_implementation.order_service.repository.OrderRepository;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    public Order create(Order order) {
        return repository.save(order);
    }

    public void cancel(UUID orderId) {
        Order order = repository.findById(orderId)
                .orElseThrow();
        order.markCancelled();
        repository.save(order);
    }

    public void complete(UUID orderId) {
        Order order = repository.findById(orderId)
                .orElseThrow();
        order.markCompleted();
        repository.save(order);
    }
}
