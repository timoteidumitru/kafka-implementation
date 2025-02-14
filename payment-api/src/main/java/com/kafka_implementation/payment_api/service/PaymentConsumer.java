package com.kafka_implementation.payment_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka_implementation.shared.dto.OrderPlacedEvent;
import com.kafka_implementation.shared.dto.PaymentResultEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class PaymentConsumer {

    private final PaymentProducer paymentProducer;
    private final ObjectMapper objectMapper;

    public PaymentConsumer(PaymentProducer paymentProducer, ObjectMapper objectMapper) {
        this.paymentProducer = paymentProducer;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order.topic", groupId = "payment-service")
    public void consumeOrderEvent(ConsumerRecord<String, String> record) {
        try {
            OrderPlacedEvent orderEvent = objectMapper.readValue(record.value(), OrderPlacedEvent.class);
            boolean isPaymentSuccessful = checkUserBalance(orderEvent.getOrderId(), orderEvent.getQuantity());

            PaymentResultEvent paymentResult = new PaymentResultEvent(orderEvent.getOrderId(), isPaymentSuccessful);
            String message = objectMapper.writeValueAsString(paymentResult);

            paymentProducer.sendPaymentResult(message);
        } catch (Exception e) {
            System.err.println("❌ Failed to process order event: " + e.getMessage());
        }
    }

    private boolean checkUserBalance(Long userId, double amount) {
        return amount <= 1000;
    }
}