package com.kafka_implementation.order_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;

import java.sql.PreparedStatement;
import java.sql.SQLWarning;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdempotencyGuardTest {

    private JdbcTemplate jdbcTemplate;
    private PreparedStatement statement;
    private IdempotencyGuard guard;

    @BeforeEach
    void setUp() throws Exception {

        jdbcTemplate = mock(JdbcTemplate.class);
        statement = mock(PreparedStatement.class);
        guard = new IdempotencyGuard(jdbcTemplate);

        when(jdbcTemplate.execute(
                anyString(),
                org.mockito.ArgumentMatchers
                        .<PreparedStatementCallback<Integer>>any()
        )).thenAnswer(invocation -> {

            PreparedStatementCallback<Integer> callback =
                    invocation.getArgument(1);

            return callback.doInPreparedStatement(statement);
        });
    }

    @Test
    void shouldReturnFalseForDuplicateKeyWarning()
            throws Exception {

        when(statement.executeUpdate()).thenReturn(0);

        when(statement.getWarnings()).thenReturn(
                new SQLWarning(
                        "Duplicate entry",
                        "23000",
                        1062
                )
        );

        assertThat(guard.tryClaim(UUID.randomUUID()))
                .isFalse();
    }

    @Test
    void shouldRejectUnexpectedWarningOnIgnoredInsert()
            throws Exception {

        when(statement.executeUpdate()).thenReturn(0);

        when(statement.getWarnings()).thenReturn(
                new SQLWarning(
                        "Data truncated",
                        "01000",
                        1265
                )
        );

        assertThatThrownBy(
                () -> guard.tryClaim(UUID.randomUUID())
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("warningCode=1265");
    }

    @Test
    void shouldRejectWarningEvenWhenInsertReportsSuccess()
            throws Exception {

        when(statement.executeUpdate()).thenReturn(1);

        when(statement.getWarnings()).thenReturn(
                new SQLWarning(
                        "Unexpected data warning",
                        "01000",
                        1265
                )
        );

        assertThatThrownBy(
                () -> guard.tryClaim(UUID.randomUUID())
        )
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectZeroRowsWithoutDuplicateWarning()
            throws Exception {

        when(statement.executeUpdate()).thenReturn(0);
        when(statement.getWarnings()).thenReturn(null);

        assertThatThrownBy(
                () -> guard.tryClaim(UUID.randomUUID())
        )
                .isInstanceOf(IllegalStateException.class);
    }
}
