package com.kafka_implementation.order_service.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class FlywayChecksumIT {

    @Container
    static final MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.0");

    @TempDir
    Path migrationDirectory;

    @Test
    void shouldRejectModificationOfAlreadyAppliedMigration()
            throws Exception {

        // GIVEN: A valid, initial V1 migration.
        Path migration = migrationDirectory.resolve(
                "V1__create_checksum_probe.sql"
        );

        Files.writeString(
                migration,
                """
                CREATE TABLE checksum_probe (
                    id BIGINT PRIMARY KEY
                );
                """
        );

        Flyway flyway = Flyway.configure()
                .dataSource(
                        mysql.getJdbcUrl(),
                        mysql.getUsername(),
                        mysql.getPassword()
                )
                .locations(
                        "filesystem:" +
                                migrationDirectory.toAbsolutePath()
                                        .toString()
                                        .replace('\\', '/')
                )
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .load();

        // WHEN: Flyway executes V1 for the first time.
        var firstResult = flyway.migrate();

        assertThat(firstResult.migrationsExecuted)
                .isEqualTo(1);

        assertThat(flyway.info().current()
                .getVersion().toString())
                .isEqualTo("1");

        // GIVEN: A developer subsequently modifies V1.
        Files.writeString(
                migration,
                """
                
                ALTER TABLE checksum_probe
                    ADD COLUMN description VARCHAR(255);
                """,
                StandardOpenOption.APPEND
        );

        // THEN: Flyway detects the checksum mismatch.
        assertThatThrownBy(() -> flyway.validate())
                .isInstanceOf(FlywayValidateException.class)
                .hasMessageContaining("checksum");

        // The recorded version remains V1.
        assertThat(flyway.info().current()
                .getVersion().toString())
                .isEqualTo("1");
    }
}
