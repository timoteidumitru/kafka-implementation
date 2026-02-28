package com.kafka_implementation.inventory_service.service;

import com.kafka_implementation.inventory_service.domain.ProcessedEvent;
import com.kafka_implementation.inventory_service.repository.ProcessedEventRepository;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class IdempotencyGuard {

    private final ProcessedEventRepository repository;

    public IdempotencyGuard(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public boolean alreadyProcessed(UUID eventId) {
        try {
            repository.saveAndFlush(new ProcessedEvent(eventId));
            return false; // first time processing
        } catch (DataIntegrityViolationException e) {
            return true; // duplicate
        }
    }
}