package org.sure_vote.repository;

import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostgresSchemaVersionTest {
    private static final String PASSWORD = "role-secret";

    private static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void start() throws SQLException {
        final var container = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres = container;
        container.start();
        for (final var role : new String[] {"surevote_app", "surevote_public", "surevote_dispute"}) {
            PostgresSchemaVersionTest.owner("create role " + role + " login password '" + PASSWORD + "'");
        }
    }

    @AfterAll
    static void stop() {
        final var container = postgres;
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void everyGrantedRoleReadsTheAppliedVersionAndOnlyTheOwnerWritesIt() throws SQLException {
        final var url = postgres.getJdbcUrl();
        final var user = postgres.getUsername();
        final var password = postgres.getPassword();
        assertThrows(RuntimeException.class, () -> JdbcSureVoteRepository.schemaVersion(url, "surevote_app", PASSWORD));
        JdbcSureVoteRepository.migrate(url, user, password);
        JdbcSureVoteRepository.grantWriter(url, user, password, "surevote_app");
        JdbcSureVoteRepository.grantReader(url, user, password, "surevote_public");
        JdbcSureVoteRepository.grantDispute(url, user, password, "surevote_dispute");
        for (final var role : new String[] {"surevote_app", "surevote_public", "surevote_dispute"}) {
            final var applied = JdbcSureVoteRepository.schemaVersion(url, role, PASSWORD);
            assertEquals(JdbcSureVoteRepository.SCHEMA_VERSION, applied);
        }
        assertThrows(SQLException.class, () -> PostgresSchemaVersionTest.as("surevote_app",
            "update flyway_schema_history set version = '99'"));
        assertThrows(SQLException.class, () -> PostgresSchemaVersionTest.as("surevote_public",
            "delete from flyway_schema_history"));
    }

    private static void owner(final String sql) throws SQLException {
        PostgresSchemaVersionTest.run(postgres.getUsername(), postgres.getPassword(), sql);
    }

    private static void as(final String role, final String sql) throws SQLException {
        PostgresSchemaVersionTest.run(role, PASSWORD, sql);
    }

    private static void run(final String user, final String password, final String sql) throws SQLException {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), user, password);
            var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
