package com.kafka_implementation.order_service.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.kafka_implementation.order_service.outbox.service.OutboxWriter;
import com.kafka_implementation.shared_events.order.OrderFailedEvent;
import com.kafka_implementation.shared_events.payment.PaymentFailedEvent;
import com.kafka_implementation.shared_events.inventory.InventoryReservationFailedEvent;
import com.kafka_implementation.shared_events.inventory.InventoryReservedEvent;
import com.kafka_implementation.shared_events.order.OrderCompletedEvent;

import static com.kafka_implementation.order_service.reconciliation.ProcessingOutcome.*;
import static com.kafka_implementation.shared_events.base.EventMetadataFactory.next;
import static com.kafka_implementation.shared_events.topics.Topics.ORDER_EVENTS_V1;

import java.util.UUID;

@Service
public class OrderEventProcessor {

    private final OrderService orderService;
    private final IdempotencyGuard idempotencyGuard;
    private final OutboxWriter outboxWriter;
    private final ProcessingOutcomeRecorder outcomeRecorder;

    public OrderEventProcessor(
            OrderService orderService,
            IdempotencyGuard idempotencyGuard,
            OutboxWriter outboxWriter,
            ProcessingOutcomeRecorder outcomeRecorder
    ) {
        this.orderService = orderService;
        this.idempotencyGuard = idempotencyGuard;
        this.outboxWriter = outboxWriter;
        this.outcomeRecorder = outcomeRecorder;
    }

    @Transactional
    public boolean processInventoryReserved(InventoryReservedEvent event) {

        UUID incomingEventId = event.metadata().eventId();
        UUID orderId = event.orderId();

        if (!idempotencyGuard.tryClaim(incomingEventId)) {
            return false;
        }

        boolean transitioned = orderService.complete(orderId);

        if (!transitioned) {

            outcomeRecorder.record(
                    incomingEventId,
                    ALREADY_IN_TARGET_STATE
            );

            return true;
        }

        OrderCompletedEvent outgoingEvent = new OrderCompletedEvent(
                next(event.metadata(), "order-service", 1),
                orderId
        );

        outboxWriter.store(
                outgoingEvent.metadata().eventId(),
                ORDER_EVENTS_V1,
                outgoingEvent
        );

        outcomeRecorder.record(
                incomingEventId,
                TRANSITION_APPLIED
        );

        return true;
    }

    @Transactional
    public boolean processInventoryFailure(
            InventoryReservationFailedEvent event
    ) {

        UUID incomingEventId = event.metadata().eventId();
        UUID orderId = event.orderId();

        // Claim the incoming event atomically.
        if (!idempotencyGuard.tryClaim(incomingEventId)) {
            return false;
        }

        // Attempt the business transition.
        boolean transitioned = orderService.cancel(orderId);

        // The Order was already CANCELLED.
        if (!transitioned) {

            outcomeRecorder.record(
                    incomingEventId,
                    ALREADY_IN_TARGET_STATE
            );

            return true;
        }

        // A genuine CREATED -> CANCELLED transition occurred.
        OrderFailedEvent outgoingEvent = new OrderFailedEvent(
                next(event.metadata(), "order-service", 1),
                orderId,
                event.reason()
        );

        // Store the resulting domain event.
        outboxWriter.store(
                outgoingEvent.metadata().eventId(),
                ORDER_EVENTS_V1,
                outgoingEvent
        );

        // Record the incoming event's business outcome.
        outcomeRecorder.record(
                incomingEventId,
                TRANSITION_APPLIED
        );

        return true;
    }

    @Transactional
    public boolean processPaymentFailure(PaymentFailedEvent event) {

        UUID incomingEventId = event.metadata().eventId();
        UUID orderId = event.orderId();

        // Claim the incoming event.
        if (!idempotencyGuard.tryClaim(incomingEventId)) {
            return false;
        }

        // Attempt the business-state transition.
        boolean transitioned = orderService.cancel(orderId);

        // The Order was already CANCELLED.
        if (!transitioned) {

            outcomeRecorder.record(
                    incomingEventId,
                    ALREADY_IN_TARGET_STATE
            );

            return true;
        }

        // A genuine CREATED -> CANCELLED transition occurred.
        OrderFailedEvent outgoingEvent = new OrderFailedEvent(
                next(event.metadata(), "order-service", 1),
                orderId,
                event.reason()
        );

        // Persist the resulting domain event.
        outboxWriter.store(
                outgoingEvent.metadata().eventId(),
                ORDER_EVENTS_V1,
                outgoingEvent
        );

        // Record the outcome of the incoming event.
        outcomeRecorder.record(
                incomingEventId,
                TRANSITION_APPLIED
        );

        return true;
    }

}
