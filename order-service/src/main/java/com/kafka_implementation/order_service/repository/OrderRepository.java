package com.kafka_implementation.order_service.repository;

import com.kafka_implementation.order_service.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
