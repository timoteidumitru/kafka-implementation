package com.kafka_implementation.order_service.service;

import com.kafka_implementation.order_service.reconciliation.ProcessingOutcome;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Component
public class ProcessingOutcomeRecorder {

    private final JdbcTemplate jdbcTemplate;

    public ProcessingOutcomeRecorder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
            UUID incomingEventId,
            ProcessingOutcome outcome
    ) {
        Objects.requireNonNull(
                incomingEventId,
                "incomingEventId must not be null"
        );

        Objects.requireNonNull(
                outcome,
                "outcome must not be null"
        );

        int updated = jdbcTemplate.update(
                """
                UPDATE processed_event
                SET outcome = ?
                WHERE event_id = UUID_TO_BIN(?)
                  AND outcome IS NULL
                """,
                outcome.name(),
                incomingEventId.toString()
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Unable to record processing outcome for event: "
                            + incomingEventId
            );
        }
    }
}
