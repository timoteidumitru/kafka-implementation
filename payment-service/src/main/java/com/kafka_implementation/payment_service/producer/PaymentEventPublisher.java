package com.kafka_implementation.payment_service.producer;

import com.kafka_implementation.payment_service.config.KafkaTopicsConfig;
import com.kafka_implementation.shared_events.base.DomainEvent;
import com.kafka_implementation.shared_events.payment.PaymentCompletedEvent;
import com.kafka_implementation.shared_events.payment.PaymentFailedEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Retry(name = "payment-producer")
    @CircuitBreaker(name = "payment-producer", fallbackMethod = "fallback")
    public void publishPaymentCompleted(PaymentCompletedEvent event) {

        kafkaTemplate.send(
                KafkaTopicsConfig.PAYMENT_EVENTS,
                event.getAggregateId().toString(),
                event
        );

        log.info("✅ PaymentCompletedEvent published for orderId={}", event.orderId());
    }

    @Retry(name = "payment-producer")
    @CircuitBreaker(name = "payment-producer", fallbackMethod = "fallback")
    public void publishPaymentFailed(PaymentFailedEvent event) {

        kafkaTemplate.send(
                KafkaTopicsConfig.PAYMENT_EVENTS,
                event.getAggregateId().toString(),
                event
        );

        log.warn("⚠️ PaymentFailedEvent published for orderId={}, reason={}",
                event.orderId(), event.reason());
    }

    @SuppressWarnings("unused")
    private void fallback(DomainEvent event, Throwable ex) {
        log.error(
                "❌ Failed to publish {} event for aggregateId={}",
                event.getEventType(),
                event.getAggregateId(),
                ex
        );
    }
}
