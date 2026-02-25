package com.kafka_implementation.payment_service.consumer;

import com.kafka_implementation.payment_service.producer.PaymentEventPublisher;
import com.kafka_implementation.payment_service.service.IdempotencyGuard;
import com.kafka_implementation.payment_service.service.PaymentService;
import com.kafka_implementation.shared_events.order.OrderCreatedEvent;
import com.kafka_implementation.shared_events.payment.PaymentCompletedEvent;
import com.kafka_implementation.shared_events.payment.PaymentFailedEvent;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.kafka_implementation.shared_events.base.EventMetadataFactory.next;
import static com.kafka_implementation.shared_events.topics.Topics.ORDER_EVENTS_V1;

@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final PaymentService paymentService;
    private final PaymentEventPublisher publisher;
    private final IdempotencyGuard idempotencyGuard;

    public PaymentEventListener(
            PaymentService paymentService,
            PaymentEventPublisher publisher,
            IdempotencyGuard idempotencyGuard
    ) {
        this.paymentService = paymentService;
        this.publisher = publisher;
        this.idempotencyGuard = idempotencyGuard;
    }

    @Retry(name = "payment-kafka")
    @CircuitBreaker(name = "payment-kafka", fallbackMethod = "fallback")
    @Bulkhead(name = "payment-kafka")
    @KafkaListener(topics = ORDER_EVENTS_V1, groupId = "payment-service")
    public void onOrderCreated(OrderCreatedEvent event) {

        UUID eventId = event.metadata().eventId();

        // ✅ Idempotency check
        if (idempotencyGuard.alreadyProcessed(eventId)) {
            log.info("Skipping already processed eventId={}", eventId);
            return;
        }

        MDC.put("eventId", eventId.toString());
        MDC.put("correlationId", event.metadata().correlationId().toString());
        MDC.put("orderId", event.orderId().toString());

        try {
            log.info(
                    "Processing payment for orderId={}, productId={}, quantity={}, price={}",
                    event.orderId(),
                    event.productId(),
                    event.quantity(),
                    event.price()
            );

            paymentService.processPayment(
                    event.orderId(),
                    event.productId(),
                    event.quantity(),
                    event.price()
            );

            publisher.publishPaymentCompleted(
                    new PaymentCompletedEvent(
                            next(event.metadata(), "payment-service"),
                            event.orderId(),
                            event.productId(),
                            event.quantity(),
                            UUID.randomUUID().toString()
                    )
            );

            log.info("Payment completed successfully for orderId={}", event.orderId());

        } finally {
            MDC.clear();
        }
    }

    private void fallback(OrderCreatedEvent event, Throwable ex) {

        MDC.put("eventId", event.metadata().eventId().toString());
        MDC.put("correlationId", event.metadata().correlationId().toString());
        MDC.put("orderId", event.orderId().toString());

        try {
            log.error(
                    "Payment failed for orderId={} due to {}",
                    event.orderId(),
                    ex.getMessage(),
                    ex
            );

            publisher.publishPaymentFailed(
                    new PaymentFailedEvent(
                            next(event.metadata(), "payment-service"),
                            event.orderId(),
                            ex.getMessage()
                    )
            );

        } finally {
            MDC.clear();
        }
    }
}