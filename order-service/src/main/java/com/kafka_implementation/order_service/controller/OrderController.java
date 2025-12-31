package com.kafka_implementation.order_service.controller;

import com.kafka_implementation.order_service.domain.CreateOrderRequest;
import com.kafka_implementation.order_service.domain.Order;
import com.kafka_implementation.order_service.producer.OrderEventPublisher;
import com.kafka_implementation.order_service.service.OrderService;
import com.kafka_implementation.shared_events.base.EventMetadata;
import com.kafka_implementation.shared_events.order.OrderCreatedEvent;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderEventPublisher publisher;

    public OrderController(OrderService orderService,
                           OrderEventPublisher publisher) {
        this.orderService = orderService;
        this.publisher = publisher;
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

        publisher.publishOrderCreated(new OrderCreatedEvent(
                EventMetadata.create("order-service", OrderCreatedEvent.VERSION),
                orderId,
                request.userId(),
                request.productId(),
                request.quantity(),
                request.price()
        ));

        return orderId;
    }
}
