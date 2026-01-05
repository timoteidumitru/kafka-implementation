package com.kafka_implementation.payment_service.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentService {

    /**
     * Saga-facing API
     * Used by PaymentEventListener
     */
    public void processPayment(UUID orderId,
                               UUID productId,
                               int quantity,
                               BigDecimal price) {

        if (quantity <= 0) {
            throw new IllegalArgumentException("Invalid quantity");
        }

        BigDecimal amount = price.multiply(BigDecimal.valueOf(quantity));

        // For now we don't have userId → can be added later
        charge(orderId, null, amount);
    }

    /**
     * Core payment logic
     * Can later integrate Stripe / PayPal / etc
     */
    public void charge(UUID orderId,
                       UUID userId,
                       BigDecimal amount) {

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid payment amount");
        }

        // Simulated payment processing
        System.out.printf(
                "[PaymentService] Charged %s for order %s (user: %s)%n",
                amount, orderId, userId
        );
    }
}
