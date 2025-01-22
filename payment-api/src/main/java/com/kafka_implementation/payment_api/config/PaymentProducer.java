package com.kafka_implementation.payment_api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.*;

@EnableKafka
@Configuration
public class PaymentProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${payment.topic.name:payment-result-topic}")
    private String paymentTopic;

    public PaymentProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendPaymentResult(String message) {
        kafkaTemplate.send(paymentTopic, message);
        System.out.println("Payment Result Sent: " + message);
    }
}

