package com.kafka_implementation.payment_service.consumer;

import com.kafka_implementation.payment_service.producer.PaymentEventPublisher;
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

@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final PaymentService paymentService;
    private final PaymentEventPublisher publisher;

    public PaymentEventListener(
            PaymentService paymentService,
            PaymentEventPublisher publisher) {
        this.paymentService = paymentService;
        this.publisher = publisher;
    }

    @KafkaListener(topics = "order.events", groupId = "payment-service")
    @Retry(name = "payment-kafka")
    @CircuitBreaker(name = "payment-kafka", fallbackMethod = "fallback")
    @Bulkhead(
            name = "payment-kafka",
            type = Bulkhead.Type.THREADPOOL
    )
    public void onOrderCreated(OrderCreatedEvent event) {

        try {
            // ===== MDC context =====
            MDC.put("eventId", event.metadata().eventId().toString());
            MDC.put("correlationId", event.metadata().correlationId().toString());
            MDC.put("orderId", event.orderId().toString());

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

            log.info("Payment completed successfully");

        } finally {
            MDC.clear();
        }
    }

    /**
     * CircuitBreaker / Retry / Bulkhead fallback
     */
    private void fallback(OrderCreatedEvent event, Throwable ex) {

        try {
            MDC.put("eventId", event.metadata().eventId().toString());
            MDC.put("correlationId", event.metadata().correlationId().toString());
            MDC.put("orderId", event.orderId().toString());

            log.error(
                    "Payment failed or throttled. Reason={}",
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
