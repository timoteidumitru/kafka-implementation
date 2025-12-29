package com.kafka_implementation.payment_service.consumer;

import com.kafka_implementation.events.base.EventMetadata;
import com.kafka_implementation.events.order.OrderCreatedEvent;
import com.kafka_implementation.events.payment.PaymentCompletedEvent;
import com.kafka_implementation.events.payment.PaymentFailedEvent;
import com.kafka_implementation.payment_service.producer.PaymentEventPublisher;
import com.kafka_implementation.payment_service.service.IdempotencyGuard;
import com.kafka_implementation.payment_service.service.PaymentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class PaymentEventListener {

    private final PaymentService paymentService;
    private final PaymentEventPublisher publisher;
    private final IdempotencyGuard idempotencyGuard;

    public PaymentEventListener(PaymentService paymentService,
                                PaymentEventPublisher publisher,
                                IdempotencyGuard idempotencyGuard) {
        this.paymentService = paymentService;
        this.publisher = publisher;
        this.idempotencyGuard = idempotencyGuard;
    }

    @KafkaListener(topics = "order.events", groupId = "payment-service")
    public void onOrderCreated(OrderCreatedEvent event) {

        // Check idempotency
        if (idempotencyGuard.alreadyProcessed(event.metadata().eventId())) return;

        try {
            // 1. Charge payment
            paymentService.charge(event.orderId(), event.userId(), event.price());

            // 2. Publish success event for Inventory service
            PaymentCompletedEvent completedEvent = new PaymentCompletedEvent(
                    nextMetadata(event.metadata()),
                    event.orderId(),
                    UUID.randomUUID().toString()
            );
            publisher.publishPaymentCompleted(completedEvent);

        } catch (Exception ex) {
            // 3. Publish failure event for saga compensation
            PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                    nextMetadata(event.metadata()),
                    event.orderId(),
                    ex.getMessage()
            );
            publisher.publishPaymentFailed(failedEvent);
        }
    }

    private EventMetadata nextMetadata(EventMetadata previous) {
        return new EventMetadata(
                UUID.randomUUID(),
                previous.correlationId(),
                Instant.now(),
                "payment-service",
                previous.version()
        );
    }
}
