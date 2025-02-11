package com.kafka_implementation.payment_api.service;

import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.*;

@EnableKafka
@Configuration
public class PaymentProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public PaymentProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendPaymentResult(String message) {
        kafkaTemplate.send("payment-result-topic", message);
        System.out.println("Payment Result Sent: " + message);
    }
}

