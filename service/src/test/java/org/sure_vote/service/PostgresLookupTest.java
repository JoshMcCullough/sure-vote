package org.sure_vote.service;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.BallotDispute;
import org.sure_vote.entity.Election;
import org.sure_vote.repository.JdbcSureVoteRepository;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostgresLookupTest {
    private static final String ROLE = "lookup_writer";

    private static final String SECRET = "lookup";

    private static PostgreSQLContainer<?> postgres;

    private static JdbcSureVoteRepository owner;

    private static JdbcSureVoteRepository writer;

    @BeforeAll
    static void start() throws SQLException {
        final var container = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres = container;
        container.start();
        PostgresLookupTest.open(container);
    }

    private static void open(final PostgreSQLContainer<?> container) throws SQLException {
        final var url = container.getJdbcUrl();
        final var user = container.getUsername();
        final var password = container.getPassword();
        JdbcSureVoteRepository.migrate(url, user, password);
        owner = JdbcSureVoteRepository.open(url, user, password);
        PostgresLookupTest.grant(url, user, password);
        writer = JdbcSureVoteRepository.open(url, ROLE, SECRET);
    }

    private static void grant(final String url, final String user, final String password) throws SQLException {
        final var connection = DriverManager.getConnection(url, user, password);
        final var statement = connection.createStatement();
        try {
            statement.execute("create role " + ROLE + " login password '" + SECRET + "'");
            statement.execute("grant usage on schema public to " + ROLE);
            statement.execute("grant select, insert, update on all tables in schema public to " + ROLE);
        }
        finally {
            statement.close();
            connection.close();
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
    void aVoterOpensTheBallotWithoutATenant() {
        final var world = SampleElection.fptpOn(owner);
        final var ballot = world.castOne(world.slotA, "Y");
        final var code = world.codes.get(ballot.getId());
        final var app = new SureVote(writer);
        final var unscoped = writer.list(Ballot.class);
        final var byCode = app.lookup(code, null);
        final var byElection = app.lookup(world.electionId, code);
        final var opened = byCode.ballot();
        final var votes = byCode.votes();
        final var vote = votes.getFirst();
        final var again = byElection.ballot();
        final var choices = byCode.choices();
        final var choice = choices.getFirst();
        assertEquals("Ada One", choice.candidateName());
        assertEquals(world.raceId, choice.raceId());
        assertEquals(List.of(), unscoped);
        assertEquals(ballot.getId(), opened.getId());
        assertEquals(ballot.getId(), again.getId());
        assertEquals(1, votes.size());
        assertEquals("Y", vote.getValue());
    }

    @Test
    void aVoterFilesAndReadsAClaimWithoutATenant() {
        final var world = SampleElection.fptpOn(owner);
        final var ballot = world.castOne(world.slotA, "Y");
        final var code = world.codes.get(ballot.getId());
        final var app = new SureVote(writer);
        final var filed = app.fileDispute(world.electionId, code, null, "Wrong race");
        final var claims = app.disputesForCode(world.electionId, code, null);
        final var count = app.disputeCount(world.electionId);
        final var unscoped = writer.list(BallotDispute.class);
        final var claim = claims.getFirst();
        assertEquals(1, claims.size());
        assertEquals(filed.getId(), claim.getId());
        assertEquals(world.tenantId, claim.getTenantId());
        assertEquals(1, count);
        assertEquals(List.of(), unscoped);
    }

    @Test
    void publicReadsNameOnlyTheirElection() {
        final var world = SampleElection.fptpOn(owner);
        final var app = new SureVote(writer);
        final var election = app.election(world.electionId);
        final var demonstration = app.demonstration(world.electionId);
        final var heads = app.heads(world.electionId);
        final var validators = app.electionValidators(world.electionId);
        final var results = app.validationResults(world.electionId);
        final var incidents = app.restoreIncidents(world.electionId);
        final var unscoped = writer.list(Election.class);
        final var missing = assertThrows(ConstraintException.class, () -> app.election(UUID.randomUUID()));
        final var wrongCode = assertThrows(
            ConstraintException.class,
            () -> app.lookup(AccessCodes.encode(AccessCodes.generate()), null)
        );
        assertEquals(world.electionId, election.getId());
        assertFalse(demonstration);
        assertFalse(heads.isEmpty());
        assertEquals(1, validators.size());
        assertEquals(List.of(), results);
        assertEquals(List.of(), incidents);
        assertEquals(List.of(), unscoped);
        assertEquals("Missing Election", missing.getMessage());
        assertEquals("no ballot for that code", wrongCode.getMessage());
    }
}
