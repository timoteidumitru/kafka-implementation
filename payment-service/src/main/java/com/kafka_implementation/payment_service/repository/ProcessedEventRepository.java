package com.kafka_implementation.payment_service.repository;

import com.kafka_implementation.payment_service.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}