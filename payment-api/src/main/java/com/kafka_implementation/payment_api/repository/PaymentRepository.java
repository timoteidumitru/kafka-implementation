package com.kafka_implementation.payment_api.repository;

import com.kafka_implementation.payment_api.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
