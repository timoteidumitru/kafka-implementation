package com.kafka_implementation.payment_service.service;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentService {

     /**
     * Process payment for an order.
     * Throws exception if payment cannot be processed.
     */
    public void charge(UUID orderId, UUID userId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid payment amount");
        }

        // Here you could integrate with a real payment provider
        // e.g., call Stripe/PayPal API, handle 3rd party responses, retries, etc.
        System.out.printf("[PaymentService] Charged %s for order %s (user: %s)%n", amount, orderId, userId);
    }
}
