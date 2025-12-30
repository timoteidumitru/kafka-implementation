package com.kafka_implementation.payment_service.producer;

import com.kafka_implementation.shared_events.payment.PaymentCompletedEvent;
import com.kafka_implementation.shared_events.payment.PaymentFailedEvent;
import com.kafka_implementation.payment_service.config.KafkaTopicsConfig;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        kafkaTemplate.send(
                KafkaTopicsConfig.PAYMENT_EVENTS,
                event.orderId().toString(),
                event
        );
    }

    public void publishPaymentFailed(PaymentFailedEvent event) {
        kafkaTemplate.send(
                KafkaTopicsConfig.PAYMENT_EVENTS,
                event.orderId().toString(),
                event
        );
    }
}
