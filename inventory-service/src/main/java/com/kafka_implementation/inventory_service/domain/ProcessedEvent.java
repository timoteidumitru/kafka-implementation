package com.kafka_implementation.inventory_service.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
@Data
public class ProcessedEvent {

    @Id
    private UUID eventId;

    protected ProcessedEvent() {}

    public ProcessedEvent(UUID eventId) {
        this.eventId = eventId;
    }
}
