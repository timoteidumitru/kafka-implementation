package com.kafka_implementation.payment_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka_implementation.payment_api.entity.Payment;
import com.kafka_implementation.payment_api.repository.PaymentRepository;
import com.kafka_implementation.events.OrderEvent;
import com.kafka_implementation.events.PaymentEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PaymentConsumer {

    private final PaymentProducer paymentProducer;
    private final ObjectMapper objectMapper;
    private final PaymentRepository paymentRepository;

    public PaymentConsumer(PaymentProducer paymentProducer, ObjectMapper objectMapper, PaymentRepository paymentRepository) {
        this.paymentProducer = paymentProducer;
        this.objectMapper = objectMapper;
        this.paymentRepository = paymentRepository;
    }

    @KafkaListener(topics = "order.topic", groupId = "payment-service")
    public void consumeOrderEvent(ConsumerRecord<String, String> record) {
        log.info("Received message from topic: order.topic, Key: {}, Partition: {}, Offset: {}",
                record.key(), record.partition(), record.offset());

        try {
            OrderEvent orderEvent = objectMapper.readValue(record.value(), OrderEvent.class);
            log.info("Deserialized OrderEvent: {}", orderEvent);

            boolean isPaymentSuccessful = checkUserBalance(orderEvent.getQuantity());
            log.info("Payment status for Order ID {}: {}", orderEvent.getOrderId(), isPaymentSuccessful);

            PaymentEvent paymentResult = new PaymentEvent(orderEvent.getOrderId(),
                    orderEvent.getProductCode(), orderEvent.getQuantity(), isPaymentSuccessful);
            String message = objectMapper.writeValueAsString(paymentResult);

            if (isPaymentSuccessful) {
                // ✅ Create a Payment instance locally instead of injecting it
                Payment payment = new Payment(null, paymentResult.getOrderId(),
                        (double) paymentResult.getQuantity(), message);

                // ✅ Save to repository
                paymentRepository.save(payment);
            }

            paymentProducer.sendPaymentResult(message);
            log.info("Sent PaymentEvent: {}", paymentResult);
        } catch (Exception e) {
            log.error("Failed to process order event: {}", e.getMessage(), e);
        }
    }

    private boolean checkUserBalance(double amount) {
        int balance = 1000;
        boolean hasSufficientBalance = balance >= amount * 5;
        log.info("Checking user balance: Requested = {}, Available = {}, Sufficient = {}",
                amount * 5, balance, hasSufficientBalance);
        return hasSufficientBalance;
    }
}

