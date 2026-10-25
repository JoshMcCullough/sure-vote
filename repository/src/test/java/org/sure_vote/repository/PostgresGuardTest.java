package org.sure_vote.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sure_vote.entity.TenantKind;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresGuardTest {
    private static PostgreSQLContainer<?> postgres;

    private static JdbcSureVoteRepository repository;

    private ParentRows rows;

    @BeforeAll
    static void start() {
        final var container = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres = container;
        container.start();
        PostgresGuardTest.open(container);
    }

    private static void open(final PostgreSQLContainer<?> container) {
        final var url = container.getJdbcUrl();
        final var user = container.getUsername();
        final var password = container.getPassword();
        JdbcSureVoteRepository.migrate(url, user, password);
        repository = JdbcSureVoteRepository.open(url, user, password);
    }

    @AfterAll
    static void stop() {
        final var container = postgres;
        if (container != null) {
            container.stop();
        }
    }

    @BeforeEach
    void setUp() {
        repository.clear();
        this.rows = new ParentRows(repository);
    }

    @Test
    void ledgerRowsCannotBeUpdatedOrDeleted() throws SQLException {
        final var ballot = this.rows.ballot(this.rows.electionId);
        final var incident = UUID.randomUUID();
        this.run(
            "insert into restore_incident (id, tenant_id, election_id) values (?, ?, ?)",
            incident,
            this.rows.tenantId,
            this.rows.electionId
        );
        this.refused("update ballot set hash = 'cd' where id = ?", ballot, "ballot is insert-only");
        this.refused("delete from ballot where id = ?", ballot, "ballot is insert-only");
        this.refused("update restore_incident set hash = 'cd' where id = ?", incident, "is insert-only");
        this.refused("delete from restore_incident where id = ?", incident, "restore_incident is insert-only");
    }

    @Test
    void everyLedgerTableIsGuarded() throws SQLException {
        final var raceId = this.rows.race();
        final var slot = this.rows.slot(raceId);
        final var vote = this.rows.vote(slot);
        final var chain = this.activeChain(raceId);
        final var entry = this.entry(chain, this.rows.vote(slot));
        final var statement = UUID.randomUUID();
        final var key = UUID.randomUUID();
        this.run(
            "insert into canvass_statement (id, tenant_id, election_id) values (?, ?, ?)",
            statement,
            this.rows.tenantId,
            this.rows.electionId
        );
        this.run("insert into held_key (id, tenant_id) values (?, ?)", key, this.rows.tenantId);
        this.guarded("vote", vote);
        this.guarded("vote_chain_entry", entry);
        this.guarded("canvass_statement", statement);
        this.guarded("held_key", key);
    }

    @Test
    void aChainHeaderMovesOnlyItsTip() throws SQLException {
        final var raceId = this.rows.race();
        final var slot = this.rows.slot(raceId);
        final var chain = this.activeChain(raceId);
        final var entry = this.entry(chain, this.rows.vote(slot));
        final var other = this.entry(chain, this.rows.vote(slot));
        this.run("update vote_chain set length = 1, hash = 'ab', latest_entry_id = ? where id = ?", entry, chain);
        this.run("update vote_chain set initial_entry_id = ? where id = ?", entry, chain);
        this.refused("update vote_chain set race_id = gen_random_uuid() where id = ?", chain, "its tip and status");
        this.refused("update vote_chain set deleted_at = now() where id = ?", chain, "its tip and status");
        this.refused(
            "update vote_chain set initial_entry_id = '" + other + "' where id = ?",
            chain,
            "initial entry is fixed"
        );
        this.refused("delete from vote_chain where id = ?", chain, "vote_chain cannot be deleted");
        assertEquals(1, this.count("select count(*) from vote_chain where id = ? and length = 1", chain));
    }

    @Test
    void aChainClosesOnceWithASignature() throws SQLException {
        final var chain = this.activeChain(this.rows.race());
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
        final var chain = this.activeChain(this.rows.race());
        this.run(
            "insert into vote_chain_validation_result"
                + " (id, tenant_id, vote_chain_id, validator_id, length, payload, hash, signature)"
                + " values (?, ?, ?, ?, 1, '{}', 'ab', 'sig')",
            result,
            this.rows.tenantId,
            chain,
            this.rows.validator()
        );
        this.run("update vote_chain_validation_result set deleted_at = now(), version = 1 where id = ?", result);
        this.refused("update vote_chain_validation_result set payload = '' where id = ?", result, "payload is fixed");
        this.refused("update vote_chain_validation_result set length = 2 where id = ?", result, "length is fixed");
        this.refused("delete from vote_chain_validation_result where id = ?", result, "cannot be deleted");
    }

    @Test
    void aRowKeepsItsTenant() throws SQLException {
        final var address = UUID.randomUUID();
        final var other = this.rows.tenant(TenantKind.PRODUCTION);
        this.run(
            "insert into address (id, tenant_id, street1) values (?, ?, '1 Main')",
            address,
            this.rows.tenantId
        );
        this.run("update address set street1 = '2 Main' where id = ?", address);
        this.refused("update address set tenant_id = '" + other + "' where id = ?", address, "tenant_id is fixed");
        this.refused("update address set tenant_id = null where id = ?", address, "tenant_id is fixed");
    }

    @Test
    void aReferenceMustExist() {
        final var missing = UUID.randomUUID();
        final var tenant = UUID.randomUUID();
        this.refusedInsert(
            "insert into race (id, tenant_id, election_id, round) values (?, ?, ?, 1)",
            "race_election_fk",
            UUID.randomUUID(),
            this.rows.tenantId,
            missing
        );
        this.refusedInsert(
            "insert into locality (id, tenant_id) values (?, ?)",
            "locality_tenant_fk",
            UUID.randomUUID(),
            tenant
        );
    }

    @Test
    void aChildTakesItsParentsTenant() throws SQLException {
        final var other = this.rows.tenant(TenantKind.PRODUCTION);
        final var otherElection = UUID.randomUUID();
        final var race = this.rows.race();
        this.run("insert into election (id, tenant_id) values (?, ?)", otherElection, other);
        this.refusedInsert(
            "insert into race (id, tenant_id, election_id, round) values (?, ?, ?, 1)",
            "race tenant_id does not match its election",
            UUID.randomUUID(),
            other,
            this.rows.electionId
        );
        this.refused(
            "update race set election_id = '" + otherElection + "' where id = ?",
            race,
            "race tenant_id does not match its election"
        );
    }

    @Test
    void aVoteStaysInItsBallotsElection() {
        final var slot = this.rows.slot(this.rows.race());
        final var ballot = this.rows.ballot(this.rows.election());
        this.refusedInsert(
            "insert into vote (id, tenant_id, ballot_id, race_candidate_id) values (?, ?, ?, ?)",
            "vote ballot and race candidate are in different elections",
            UUID.randomUUID(),
            this.rows.tenantId,
            ballot,
            slot
        );
    }

    @Test
    void anAccessHashIsOnOneBallotInTheDatabase() throws SQLException {
        final var other = this.rows.election();
        final var ballot = "insert into ballot (id, tenant_id, election_id, voting_precinct_id, voter_access_hash)"
            + " values (?, ?, ?, ?, ?)";
        this.run(ballot, UUID.randomUUID(), this.rows.tenantId, this.rows.electionId, this.rows.precinctId, "shared");
        this.refusedInsert(
            ballot,
            "ballot_access_hash",
            UUID.randomUUID(),
            this.rows.tenantId,
            other,
            this.rows.precinctId,
            "shared"
        );
    }

    @Test
    void aBallotNeedsACurrentPrecinctAssignment() throws SQLException {
        final var precinct = UUID.randomUUID();
        final var ballot = "insert into ballot (id, tenant_id, election_id, voting_precinct_id, voter_access_hash)"
            + " values (?, ?, ?, ?, ?)";
        this.run("insert into voting_precinct (id, tenant_id) values (?, ?)", precinct, this.rows.tenantId);
        this.refusedInsert(
            ballot,
            "ballot precinct is not assigned to its election",
            UUID.randomUUID(),
            this.rows.tenantId,
            this.rows.electionId,
            precinct,
            "unassigned"
        );
        this.run(
            "update election_voting_precinct_assignment set assignment_status = 'historical' where election_id = ?",
            this.rows.electionId
        );
        this.refusedInsert(
            ballot,
            "ballot precinct is not assigned to its election",
            UUID.randomUUID(),
            this.rows.tenantId,
            this.rows.electionId,
            this.rows.precinctId,
            "historical"
        );
    }

    @Test
    void anEntryStaysOnItsVotesRaceChain() throws SQLException {
        final var vote = this.rows.vote(this.rows.slot(this.rows.race()));
        final var chain = this.activeChain(this.rows.race());
        final var failure = assertThrows(SQLException.class, () -> this.entry(chain, vote));
        final var text = failure.getMessage();
        assertTrue(text.contains("chain entry is not on the chain of its vote's race"), text);
    }

    @Test
    void aProductionElectionNeedsAnOutsideValidator() throws SQLException {
        final var activate = "update election set status = 'active' where id = ?";
        final var showcase = this.rows.tenant(TenantKind.SHOWCASE);
        final var demo = UUID.randomUUID();
        this.refused(activate, this.rows.electionId, "a production election needs an outside validator");
        this.run("insert into election (id, tenant_id, status) values (?, ?, 'active')", demo, showcase);
        this.run(
            "insert into tenant_validator (id, tenant_id, validator_id) values (?, ?, ?)",
            UUID.randomUUID(),
            this.rows.tenantId,
            this.rows.validator()
        );
        this.run(activate, this.rows.electionId);
        assertEquals(1, this.count("select count(*) from election where id = ? and status = 'active'", demo));
        assertEquals(
            1,
            this.count("select count(*) from election where id = ? and status = 'active'", this.rows.electionId)
        );
    }

    private void guarded(final String table, final UUID id) {
        this.refused("update " + table + " set created_by = created_by where id = ?", id, table + " is insert-only");
        this.refused("delete from " + table + " where id = ?", id, table + " is insert-only");
    }

    private UUID activeChain(final UUID raceId) throws SQLException {
        final var chain = UUID.randomUUID();
        this.run(
            "insert into vote_chain (id, tenant_id, race_id, status, length, hash)"
                + " values (?, ?, ?, 'active', 0, 'e0')",
            chain,
            this.rows.tenantId,
            raceId
        );
        return chain;
    }

    private UUID entry(final UUID chain, final UUID vote) throws SQLException {
        final var entry = UUID.randomUUID();
        final var index = this.count("select count(*) from vote_chain_entry where vote_chain_id = ?", chain);
        this.run(
            "insert into vote_chain_entry (id, tenant_id, vote_chain_id, vote_id, index, nonce)"
                + " values (?, ?, ?, ?, ?, ?)",
            entry,
            this.rows.tenantId,
            chain,
            vote,
            index,
            "n-" + entry
        );
        return entry;
    }

    private void refusedInsert(final String sql, final String message, final Object... values) {
        final var failure = assertThrows(SQLException.class, () -> this.run(sql, values));
        final var text = failure.getMessage();
        assertTrue(text.contains(message), text);
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
