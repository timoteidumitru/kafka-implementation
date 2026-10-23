package com.kafka_implementation.order_service.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class FlywayPartialMigrationIT {

    @Container
    static final MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.0");

    @TempDir
    Path migrationDirectory;

    @Test
    void shouldExposePartialSchemaChangesWhenMysqlMigrationFails()
            throws Exception {

        // GIVEN: A migration containing two CREATE TABLE
        // statements for the same table.
        Path migration = migrationDirectory.resolve(
                "V1__broken_migration.sql"
        );

        Files.writeString(
                migration,
                """
                CREATE TABLE migration_probe (
                    id BIGINT PRIMARY KEY
                );

                CREATE TABLE migration_probe (
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

        // WHEN: Flyway attempts to execute the migration.
        assertThatThrownBy(flyway::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("migration_probe");

        // THEN: The first CREATE TABLE has persisted,
        // despite the failed migration.
        try (var connection = DriverManager.getConnection(
                mysql.getJdbcUrl(),
                mysql.getUsername(),
                mysql.getPassword()
        )) {

            try (var statement = connection.prepareStatement(
                    """
                    SELECT COUNT(*)
                    FROM information_schema.tables
                    WHERE table_schema = DATABASE()
                      AND table_name = 'migration_probe'
                    """
            );
                 var result = statement.executeQuery()) {

                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(1);
            }

            // Flyway records the failed migration.
            try (var statement = connection.prepareStatement(
                    """
                    SELECT COUNT(*)
                    FROM flyway_schema_history
                    WHERE version = '1'
                      AND success = 0
                    """
            );
                 var result = statement.executeQuery()) {

                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
        }
    }
}
