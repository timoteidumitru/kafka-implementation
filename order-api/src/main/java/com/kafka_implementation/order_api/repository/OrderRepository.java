package com.kafka_implementation.order_api.repository;

import com.kafka_implementation.order_api.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
