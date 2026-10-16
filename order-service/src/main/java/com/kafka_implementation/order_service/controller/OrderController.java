package com.kafka_implementation.order_service.controller;

import com.kafka_implementation.order_service.domain.CreateOrderRequest;
import com.kafka_implementation.order_service.domain.Order;
import com.kafka_implementation.order_service.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public UUID createOrder(@RequestBody CreateOrderRequest request) {

        UUID orderId = UUID.randomUUID();

        Order order = new Order(
                orderId,
                request.userId(),
                request.productId(),
                request.quantity(),
                request.price()
        );

        orderService.create(order);

        return orderId;
    }
}
