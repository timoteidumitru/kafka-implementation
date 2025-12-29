package com.kafka_implementation.payment_service.service;

import com.kafka_implementation.payment_service.domain.ProcessedEvent;
import com.kafka_implementation.payment_service.repository.ProcessedEventRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class IdempotencyGuard {

    private final ProcessedEventRepository repository;

    public IdempotencyGuard(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public boolean alreadyProcessed(UUID eventId) {
        if (repository.existsById(eventId)) {
            return true;
        }

        repository.save(new ProcessedEvent(eventId, Instant.now()));
        return false;
    }
}
