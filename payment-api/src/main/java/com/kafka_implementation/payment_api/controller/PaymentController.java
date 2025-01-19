package com.kafka_implementation.payment_api.controller;

import com.kafka_implementation.payment_api.entity.Payment;
import com.kafka_implementation.payment_api.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private static final String PAYMENT_RESULT_TOPIC = "payment-result-topic";

    @PostMapping
    public String processPayment(@RequestBody Payment payment) {
        // Simulate payment validation
        if (payment.getAmount() > 0) {
            payment.setStatus("VALIDATED");
        } else {
            payment.setStatus("DECLINED");
        }

        Payment savedPayment = paymentRepository.save(payment);

        // Publish payment result to Kafka
        String message = String.format("Payment ID: %d, Status: %s", savedPayment.getId(), savedPayment.getStatus());
        kafkaTemplate.send(PAYMENT_RESULT_TOPIC, message);

        return "Payment processed successfully!";
    }
}

