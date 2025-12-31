package com.kafka_implementation.order_service.service;

import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class IdempotencyGuard {

    private final Set<UUID> processedEvents = ConcurrentHashMap.newKeySet();

    public boolean alreadyProcessed(UUID eventId) {
        return !processedEvents.add(eventId);
    }
}
