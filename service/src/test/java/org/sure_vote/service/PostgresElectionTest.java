package org.sure_vote.service;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.ChainStatus;
import org.sure_vote.entity.ElectionStatus;
import org.sure_vote.entity.Outcome;
import org.sure_vote.entity.VoteChain;
import org.sure_vote.repository.JdbcSureVoteRepository;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostgresElectionTest {
    private static PostgreSQLContainer<?> postgres;
    private static JdbcSureVoteRepository repository;

    @BeforeAll
    static void start() {
        final var container = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres = container;
        container.start();
        PostgresElectionTest.open(container);
    }

    @AfterAll
    static void stop() {
        final var container = postgres;
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void recordsAndClosesAFirstPastThePostElection() {
        final var world = SampleElection.fptpOn(repository);
        final var ballot = world.castOne(world.slotA, "Y");
        final var code = world.codes.get(ballot.getId());
        this.assertClosedBallot(world, ballot, code);
    }

    @Test
    void anOuterFailureRollsBackTheImportedBallot() {
        final var world = SampleElection.fptpOn(repository);
        final var before = repository.list(Ballot.class);
        this.assertRolledBack(world, before);
    }

    private static void open(final PostgreSQLContainer<?> container) {
        final var url = container.getJdbcUrl();
        final var user = container.getUsername();
        final var password = container.getPassword();
        JdbcSureVoteRepository.migrate(url, user, password);
        repository = JdbcSureVoteRepository.open(url, user, password);
    }

    private void assertClosedBallot(final SampleElection world, final Ballot ballot, final String code) {
        world.close();
        this.assertHistorical(world, ballot, code);
    }

    private void assertHistorical(final SampleElection world, final Ballot ballot, final String code) {
        final var election = world.app.election(world.electionId);
        final var found = world.app.lookup(world.electionId, code);
        final var lookedUp = found.ballot();
        assertEquals(ElectionStatus.HISTORICAL, election.getStatus());
        assertEquals(ballot.getId(), lookedUp.getId());
        world.assertOutcome(world.slotA, Outcome.WON, 1, 1);
        world.assertOutcome(world.slotB, Outcome.LOST, null, 0);
        this.assertRaceClosed(world);
    }

    private void assertRaceClosed(final SampleElection world) {
        final var chains = repository.list(VoteChain.class);
        final var chain = this.chainFor(chains, world.raceId);
        assertEquals(ChainStatus.CLOSED, chain.getStatus());
        assertNotNull(chain.getSignature());
    }

    private VoteChain chainFor(final List<VoteChain> chains, final UUID raceId) {
        for (final var chain : chains) {
            final var sameRace = raceId.equals(chain.getRaceId());
            if (sameRace) {
                return chain;
            }
        }
        throw new IllegalStateException("missing chain");
    }

    private void assertRolledBack(final SampleElection world, final List<Ballot> before) {
        final var failure = assertThrows(IllegalStateException.class, () -> this.castInside(world));
        final var after = repository.list(Ballot.class);
        assertEquals("stop", failure.getMessage());
        assertEquals(before.size(), after.size());
    }

    private void castInside(final SampleElection world) {
        repository.transaction(() -> this.castAndStop(world));
    }

    private String castAndStop(final SampleElection world) {
        world.castOne(world.slotA, "Y");
        throw new IllegalStateException("stop");
    }
}
