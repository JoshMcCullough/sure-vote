package org.sure_vote.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.VoteChain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestoreFreezeTest {
    private static final String FROZEN = "Election is frozen after a restore";

    @Test
    void aDivergentRestoreFreezesTheElection() {
        final var world = SampleElection.fptp();
        final var ballot = this.frozenWithBallot(world);
        final var code = world.codeFor(ballot);
        final var id = world.electionId;
        final var results = world.app.validationResults(id);
        assertNotNull(this.frozenAt(world));
        this.assertFrozen(() -> world.castOne(world.slotA, "Y"));
        this.assertFrozen(world::close);
        this.assertFrozen(() -> world.app.heads(id));
        assertEquals(2, results.size());
        this.assertFrozen(() -> world.app.lookup(id, code));
    }

    @Test
    void filingTheIncidentLiftsTheFreeze() {
        final var world = SampleElection.fptp();
        final var ballot = this.frozenWithBallot(world);
        final var heads = this.headsAfterFiling(world);
        final var found = world.app.lookup(world.electionId, world.codeFor(ballot));
        final var lookedUp = found.ballot();
        final var again = world.app.checkRestore(world.admin());
        assertNull(this.frozenAt(world));
        assertEquals(1, heads.size());
        assertEquals(ballot.getId(), lookedUp.getId());
        assertEquals(List.of(), again);
    }

    @Test
    void aMatchingRestoreIsNotFrozen() {
        final var world = SampleElection.fptp();
        final var frozen = this.checkedAfterWitness(world);
        assertEquals(List.of(), frozen);
        assertNull(this.frozenAt(world));
    }

    @Test
    void anUpcomingElectionIsNotChecked() {
        final var world = SampleElection.upcomingFptp();
        final var frozen = world.app.checkRestore(world.admin());
        assertEquals(List.of(), frozen);
    }

    @Test
    void onlyASystemAdminRunsTheCheck() {
        final var world = SampleElection.fptp();
        final var stranger = UUID.randomUUID();
        assertThrows(RuntimeException.class, () -> world.app.checkRestore(stranger));
    }

    private Ballot frozenWithBallot(final SampleElection world) {
        final var ballot = world.castOne(world.slotB, "Y");
        final var chain = RestoreFixture.chain(world);
        final var frozen = this.freeze(world, chain);
        assertEquals(List.of(world.electionId), frozen);
        return ballot;
    }

    private List<UUID> freeze(final SampleElection world, final VoteChain chain) {
        RestoreFixture.witness(world, chain);
        RestoreFixture.witnessLostHead(world, chain);
        return world.app.checkRestore(world.admin());
    }

    private List<UUID> checkedAfterWitness(final SampleElection world) {
        RestoreFixture.castAndWitness(world);
        return world.app.checkRestore(world.admin());
    }

    private List<HeadNotice> headsAfterFiling(final SampleElection world) {
        final var payload = world.app.restoreIncidentPayload(world.electionId, "Disk lost", RestoreFixture.WHEN);
        final var raw = Digests.unhex(Digests.sha256Hex(payload));
        final var seed = world.operator.seed();
        final var signature = Signatures.sign(seed, raw);
        final var request = new RestoreRequest("Disk lost", RestoreFixture.WHEN, signature);
        world.app.fileRestoreIncident(world.electionId, request, world.actor);
        return world.app.heads(world.electionId);
    }

    private Instant frozenAt(final SampleElection world) {
        final var found = world.repository.find(Election.class, world.electionId);
        final var election = found.orElseThrow();
        return election.getFrozenAt();
    }

    private void assertFrozen(final Executable work) {
        final var failure = assertThrows(ConstraintException.class, work);
        assertEquals(FROZEN, failure.getMessage());
    }
}
