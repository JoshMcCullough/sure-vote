package org.sure_vote.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresGuardTest {
    private static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void start() {
        final var container = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres = container;
        container.start();
        JdbcSureVoteRepository.migrate(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }

    @AfterAll
    static void stop() {
        final var container = postgres;
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void ledgerRowsCannotBeUpdatedOrDeleted() throws SQLException {
        final var ballot = UUID.randomUUID();
        final var incident = UUID.randomUUID();
        this.run("insert into ballot (id, tenant_id, hash) values (?, ?, 'ab')", ballot, UUID.randomUUID());
        this.run("insert into restore_incident (id, tenant_id) values (?, ?)", incident, UUID.randomUUID());
        this.refused("update ballot set hash = 'cd' where id = ?", ballot, "ballot is insert-only");
        this.refused("delete from ballot where id = ?", ballot, "ballot is insert-only");
        this.refused("update restore_incident set hash = 'cd' where id = ?", incident, "is insert-only");
        this.refused("delete from restore_incident where id = ?", incident, "restore_incident is insert-only");
    }

    @Test
    void everyLedgerTableIsGuarded() throws SQLException {
        this.guarded("vote", "insert into vote (id, tenant_id) values (?, ?)");
        this.guarded("vote_chain_entry", "insert into vote_chain_entry (id, tenant_id, index) values (?, ?, 0)");
        this.guarded("canvass_statement", "insert into canvass_statement (id, tenant_id) values (?, ?)");
        this.guarded("held_key", "insert into held_key (id, tenant_id) values (?, ?)");
    }

    @Test
    void aChainHeaderMovesOnlyItsTip() throws SQLException {
        final var chain = this.activeChain();
        final var entry = UUID.randomUUID();
        this.run("update vote_chain set length = 1, hash = 'ab', latest_entry_id = ? where id = ?", entry, chain);
        this.run("update vote_chain set initial_entry_id = ? where id = ?", entry, chain);
        this.refused("update vote_chain set race_id = gen_random_uuid() where id = ?", chain, "its tip and status");
        this.refused("update vote_chain set deleted_at = now() where id = ?", chain, "its tip and status");
        this.refused(
            "update vote_chain set initial_entry_id = gen_random_uuid() where id = ?",
            chain,
            "initial entry is fixed"
        );
        this.refused("delete from vote_chain where id = ?", chain, "vote_chain cannot be deleted");
        assertEquals(1, this.count("select count(*) from vote_chain where id = ? and length = 1", chain));
    }

    @Test
    void aChainClosesOnceWithASignature() throws SQLException {
        final var chain = this.activeChain();
        this.refused("update vote_chain set status = 'closed' where id = ?", chain, "needs a hash and a signature");
        this.run("update vote_chain set status = 'closed', signature = 'sig' where id = ?", chain);
        this.refused(
            "update vote_chain set status = 'active' where id = ?",
            chain,
            "status may move only from active to closed"
        );
        assertEquals(1, this.count("select count(*) from vote_chain where id = ? and status = 'closed'", chain));
    }

    @Test
    void aValidationResultKeepsItsSignedFields() throws SQLException {
        final var result = UUID.randomUUID();
        this.run(
            "insert into vote_chain_validation_result (id, tenant_id, length, payload, hash, signature)"
                + " values (?, ?, 1, '{}', 'ab', 'sig')",
            result,
            UUID.randomUUID()
        );
        this.run("update vote_chain_validation_result set deleted_at = now(), version = 1 where id = ?", result);
        this.refused("update vote_chain_validation_result set payload = '' where id = ?", result, "payload is fixed");
        this.refused("update vote_chain_validation_result set length = 2 where id = ?", result, "length is fixed");
        this.refused("delete from vote_chain_validation_result where id = ?", result, "cannot be deleted");
    }

    @Test
    void aRowKeepsItsTenant() throws SQLException {
        final var address = UUID.randomUUID();
        this.run("insert into address (id, tenant_id, street1) values (?, ?, '1 Main')", address, UUID.randomUUID());
        this.run("update address set street1 = '2 Main' where id = ?", address);
        this.refused("update address set tenant_id = gen_random_uuid() where id = ?", address, "tenant_id is fixed");
        this.refused("update address set tenant_id = null where id = ?", address, "tenant_id is fixed");
    }

    private void guarded(final String table, final String insert) throws SQLException {
        final var id = UUID.randomUUID();
        this.run(insert, id, UUID.randomUUID());
        this.refused("update " + table + " set created_by = created_by where id = ?", id, table + " is insert-only");
        this.refused("delete from " + table + " where id = ?", id, table + " is insert-only");
    }

    private UUID activeChain() throws SQLException {
        final var chain = UUID.randomUUID();
        this.run(
            "insert into vote_chain (id, tenant_id, race_id, status, length, hash)"
                + " values (?, ?, ?, 'active', 0, 'e0')",
            chain,
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        return chain;
    }

    private void refused(final String sql, final UUID id, final String message) {
        final var failure = assertThrows(SQLException.class, () -> this.run(sql, id));
        final var text = failure.getMessage();
        assertTrue(text.contains(message), text);
    }

    private void run(final String sql, final Object... values) throws SQLException {
        final var connection = this.connect();
        try {
            this.execute(connection, sql, values);
        }
        finally {
            connection.close();
        }
    }

    private void execute(final Connection connection, final String sql, final Object... values) throws SQLException {
        final var statement = connection.prepareStatement(sql);
        try {
            this.bind(statement, values);
            statement.executeUpdate();
        }
        finally {
            statement.close();
        }
    }

    private void bind(final PreparedStatement statement, final Object... values) throws SQLException {
        var index = 0;
        for (final var value : values) {
            index = index + 1;
            statement.setObject(index, value);
        }
    }

    private int count(final String sql, final UUID id) throws SQLException {
        final var connection = this.connect();
        try {
            return this.single(connection, sql, id);
        }
        finally {
            connection.close();
        }
    }

    private int single(final Connection connection, final String sql, final UUID id) throws SQLException {
        final var statement = connection.prepareStatement(sql);
        try {
            statement.setObject(1, id);
            return this.first(statement.executeQuery());
        }
        finally {
            statement.close();
        }
    }

    private int first(final ResultSet rows) throws SQLException {
        rows.next();
        return rows.getInt(1);
    }

    private Connection connect() throws SQLException {
        final var container = postgres;
        return DriverManager.getConnection(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }
}
