package com.kafka_implementation.order_service.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrderEventProcessor {

    private final OrderService orderService;
    private final IdempotencyGuard idempotencyGuard;

    public OrderEventProcessor(
            OrderService orderService,
            IdempotencyGuard idempotencyGuard
    ) {
        this.orderService = orderService;
        this.idempotencyGuard = idempotencyGuard;
    }

    @Transactional
    public boolean processCancellation(UUID eventId, UUID orderId) {
        if (idempotencyGuard.alreadyProcessed(eventId)) {
            return false;
        }

        orderService.cancel(orderId);
        return true;
    }

    @Transactional
    public boolean processCompletion(UUID eventId, UUID orderId) {
        if (idempotencyGuard.alreadyProcessed(eventId)) {
            return false;
        }

        orderService.complete(orderId);
        return true;
    }
}
