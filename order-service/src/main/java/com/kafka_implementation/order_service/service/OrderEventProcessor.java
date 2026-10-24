package com.kafka_implementation.order_service.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.kafka_implementation.order_service.outbox.service.OutboxWriter;
import com.kafka_implementation.shared_events.order.OrderFailedEvent;
import com.kafka_implementation.shared_events.payment.PaymentFailedEvent;
import com.kafka_implementation.shared_events.inventory.InventoryReservationFailedEvent;
import com.kafka_implementation.shared_events.inventory.InventoryReservedEvent;
import com.kafka_implementation.shared_events.order.OrderCompletedEvent;

import static com.kafka_implementation.shared_events.base.EventMetadataFactory.next;
import static com.kafka_implementation.shared_events.topics.Topics.ORDER_EVENTS_V1;

import java.util.UUID;

@Service
public class OrderEventProcessor {

    private final OrderService orderService;
    private final IdempotencyGuard idempotencyGuard;
    private final OutboxWriter outboxWriter;

    public OrderEventProcessor(
            OrderService orderService,
            IdempotencyGuard idempotencyGuard,
            OutboxWriter outboxWriter
    ) {
        this.orderService = orderService;
        this.idempotencyGuard = idempotencyGuard;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public boolean processInventoryReserved(
            InventoryReservedEvent event
    ) {
        UUID incomingEventId = event.metadata().eventId();
        UUID orderId = event.orderId();

        // 1. Claim this incoming Kafka event once.
        if (!idempotencyGuard.tryClaim(incomingEventId)) {
            return false;
        }

        // 2. Update the Order.
        orderService.complete(orderId);

        // 3. Construct the outgoing domain event.
        OrderCompletedEvent outgoingEvent =
                new OrderCompletedEvent(
                        next(event.metadata(), "order-service", 1),
                        orderId
                );

        // 4. Persist the outgoing event in the same transaction.
        outboxWriter.store(
                outgoingEvent.metadata().eventId(),
                ORDER_EVENTS_V1,
                outgoingEvent
        );

        return true;
    }

    @Transactional
    public boolean processInventoryFailure(
            InventoryReservationFailedEvent event
    ) {
        UUID incomingEventId = event.metadata().eventId();
        UUID orderId = event.orderId();

        if (!idempotencyGuard.tryClaim(incomingEventId)) {
            return false;
        }

        orderService.cancel(orderId);

        OrderFailedEvent outgoingEvent = new OrderFailedEvent(
                next(event.metadata(), "order-service", 1),
                orderId,
                event.reason()
        );

        outboxWriter.store(
                outgoingEvent.metadata().eventId(),
                ORDER_EVENTS_V1,
                outgoingEvent
        );

        return true;
    }

    @Transactional
    public boolean processPaymentFailure(PaymentFailedEvent event) {

        UUID incomingEventId = event.metadata().eventId();
        UUID orderId = event.orderId();

        // 1. Claim the incoming event inside this transaction.
        if (!idempotencyGuard.tryClaim(incomingEventId)) {
            return false;
        }

        // 2. Update the Order.
        orderService.cancel(orderId);

        // 3. Construct the outgoing domain event.
        OrderFailedEvent outgoingEvent = new OrderFailedEvent(
                next(event.metadata(), "order-service", 1),
                orderId,
                event.reason()
        );

        // 4. Persist the outgoing event in MySQL, not Kafka.
        outboxWriter.store(
                outgoingEvent.metadata().eventId(),
                ORDER_EVENTS_V1,
                outgoingEvent
        );

        return true;
    }

    @Transactional
    public boolean processCancellation(UUID eventId, UUID orderId) {
        if (!idempotencyGuard.tryClaim(eventId)) {
            return false;
        }

        orderService.cancel(orderId);
        return true;
    }

    @Transactional
    public boolean processCompletion(UUID eventId, UUID orderId) {
        if (!idempotencyGuard.tryClaim(eventId)) {
            return false;
        }

        orderService.complete(orderId);
        return true;
    }
}
