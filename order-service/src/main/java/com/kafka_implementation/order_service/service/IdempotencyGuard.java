
package com.kafka_implementation.order_service.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.sql.SQLWarning;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Component
public class IdempotencyGuard {

    private static final int MYSQL_DUPLICATE_KEY = 1062;

    private final JdbcTemplate jdbcTemplate;

    public IdempotencyGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryClaim(UUID eventId) {

        Objects.requireNonNull(
                eventId,
                "eventId must not be null"
        );

        byte[] eventIdBytes = ByteBuffer.allocate(16)
                .putLong(eventId.getMostSignificantBits())
                .putLong(eventId.getLeastSignificantBits())
                .array();

        Integer result = jdbcTemplate.execute(
                """
                INSERT IGNORE INTO processed_event
                    (event_id, processed_at)
                VALUES (?, ?)
                """,
                (PreparedStatementCallback<Integer>) ps -> {

                    ps.setBytes(1, eventIdBytes);
                    ps.setTimestamp(
                            2,
                            Timestamp.from(Instant.now())
                    );

                    int affectedRows = ps.executeUpdate();

                    // Inspect the warnings from THIS statement,
                    // on THIS database connection.
                    SQLWarning warning = ps.getWarnings();

                    if (affectedRows == 1 && warning == null) {
                        return 1;
                    }

                    if (affectedRows == 0
                            && warning != null
                            && warning.getErrorCode()
                            == MYSQL_DUPLICATE_KEY
                            && warning.getNextWarning() == null) {
                        return 0;
                    }

                    throw new IllegalStateException(
                            "Unexpected result while claiming " +
                                    "incoming event. affectedRows=" +
                                    affectedRows +
                                    ", warningCode=" +
                                    (warning == null
                                            ? "none"
                                            : warning.getErrorCode())
                    );
                }
        );

        if (result == null) {
            throw new IllegalStateException(
                    "Incoming event claim returned null"
            );
        }

        return result == 1;
    }
}
