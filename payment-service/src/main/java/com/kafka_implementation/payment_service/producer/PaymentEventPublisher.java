package com.kafka_implementation.payment_service.producer;

import com.kafka_implementation.shared_events.base.DomainEvent;
import com.kafka_implementation.shared_events.payment.PaymentCompletedEvent;
import com.kafka_implementation.shared_events.payment.PaymentFailedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import static com.kafka_implementation.shared_events.topics.Topics.PAYMENT_EVENTS_V1;

@Slf4j
@Component
public class PaymentEventPublisher {

    private final KafkaTemplate<String, DomainEvent> kafkaTemplate;

    public PaymentEventPublisher(KafkaTemplate<String, DomainEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        publish(event);
    }

    public void publishPaymentFailed(PaymentFailedEvent event) {
        publish(event);
    }

    private void publish(DomainEvent event) {
        kafkaTemplate.send(
                PAYMENT_EVENTS_V1,
                event.getAggregateId().toString(),
                event
        );

        log.info("Payment event {} published for aggregateId={}",
                event.getEventType(),
                event.getAggregateId());
    }
}