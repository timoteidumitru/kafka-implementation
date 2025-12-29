package com.kafka_implementation.payment_api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.*;

@EnableKafka
@Configuration
@Slf4j
public class PaymentProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public PaymentProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendPaymentResult(String message) {
        log.info("Payment Result Sent: {}", message);

        kafkaTemplate.send("payment-result-topic", message);
    }
}
