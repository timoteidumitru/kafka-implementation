package com.kafka_implementation.payment_service.consumer;

import com.kafka_implementation.payment_service.producer.PaymentEventPublisher;
import com.kafka_implementation.payment_service.service.PaymentService;
import com.kafka_implementation.shared_events.order.OrderCreatedEvent;
import com.kafka_implementation.shared_events.payment.PaymentCompletedEvent;
import com.kafka_implementation.shared_events.payment.PaymentFailedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.kafka_implementation.shared_events.base.EventMetadataFactory.next;

@Component
public class PaymentEventListener {

    private final PaymentService paymentService;
    private final PaymentEventPublisher publisher;

    public PaymentEventListener(PaymentService paymentService,
                                PaymentEventPublisher publisher) {
        this.paymentService = paymentService;
        this.publisher = publisher;
    }

    @KafkaListener(topics = "order.events", groupId = "payment-service")
    public void onOrderCreated(OrderCreatedEvent event) {

        try {
            /* =========================
               1. Execute payment logic
               ========================= */

            paymentService.processPayment(
                    event.orderId(),
                    event.productId(),
                    event.quantity(),
                    event.price()
            );

            /* =========================
               2. Emit PAYMENT COMPLETED
               ========================= */

            publisher.publishPaymentCompleted(
                    new PaymentCompletedEvent(
                            next(event.metadata(), "payment-service"),
                            event.orderId(),
                            event.productId(),
                            event.quantity(),
                            UUID.randomUUID().toString()
                    )
            );

        } catch (Exception ex) {

            /* =========================
               3. Emit PAYMENT FAILED
               ========================= */

            publisher.publishPaymentFailed(
                    new PaymentFailedEvent(
                            next(event.metadata(), "payment-service"),
                            event.orderId(),
                            ex.getMessage()
                    )
            );
        }
    }
}
