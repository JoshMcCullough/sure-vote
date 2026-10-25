package org.sure_vote.service;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sure_vote.entity.Outcome;
import org.sure_vote.entity.RestoreIncident;
import org.sure_vote.entity.VoteChain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestoreRulesTest {
    private static final Instant WHEN = RestoreFixture.WHEN;

    @Test
    void aDivergentRestoreIsRecordedWithTheOperatorSignature() {
        final var world = SampleElection.fptp();
        final var chain = RestoreFixture.restoredAfterLoss(world);
        final var saved = this.file(world, "Disk lost after import batch 4", world.operator);
        final var rows = world.app.restoreIncidents(world.electionId);
        final var expected = this.expectedPayload(world, chain, "Disk lost after import batch 4");
        assertEquals(1, rows.size());
        assertEquals(expected, saved.getPayload());
        assertEquals(Digests.sha256Hex(expected), saved.getHash());
        assertEquals(world.electionId, saved.getElectionId());
        assertEquals(world.tenantId, saved.getTenantId());
        assertEquals(world.actor, saved.getCreatedBy());
    }

    @Test
    void theIncidentDoesNotChangeTheChainOrTheTally() {
        final var world = SampleElection.fptp();
        final var before = RestoreFixture.restoredAfterLoss(world);
        final var after = this.filedThenClosed(world);
        assertEquals(before.getLength(), after.getLength());
        assertEquals(before.getHash(), after.getHash());
        world.assertOutcome(world.slotA, Outcome.WON, 1, 1);
    }

    @Test
    void aRestoreThatMatchesTheWitnessNeedsNoIncident() {
        final var world = SampleElection.fptp();
        final var chain = RestoreFixture.castAndWitness(world);
        final var failure = this.refused(world);
        assertEquals("the restored heads match the outside witnesses", failure.getMessage());
        assertEquals(1, chain.getLength());
    }

    @Test
    void aRestoreThatExtendsTheWitnessNeedsNoIncident() {
        final var world = SampleElection.fptp();
        final var failure = this.refusedAfterGrowth(world);
        assertEquals("the restored heads match the outside witnesses", failure.getMessage());
    }

    @Test
    void anUnwitnessedElectionNeedsNoIncident() {
        final var world = SampleElection.fptp();
        final var failure = this.refusedUnwitnessed(world);
        assertEquals("the restored heads match the outside witnesses", failure.getMessage());
    }

    @Test
    void theOperatorSignatureMustMatch() {
        final var world = SampleElection.fptp();
        final var stranger = Signatures.generate();
        final var chain = RestoreFixture.restoredAfterLoss(world);
        final var failure = assertThrows(ConstraintException.class, () -> this.file(world, "Disk lost", stranger));
        final var garbled = assertThrows(ConstraintException.class, () -> this.fileSigned(world, "Disk lost", "zz"));
        assertEquals("Operator signature does not match the restore incident", failure.getMessage());
        assertEquals("Operator signature does not match the restore incident", garbled.getMessage());
        assertEquals(1, chain.getLength());
    }

    @Test
    void anIncidentNeedsAReasonAndATimestamp() {
        final var world = SampleElection.fptp();
        final var blank = assertThrows(ConstraintException.class, () -> this.file(world, "  ", world.operator));
        final var missing = assertThrows(ConstraintException.class, () -> this.undated(world));
        assertEquals("restore reason is required", blank.getMessage());
        assertEquals("timestamp is required", missing.getMessage());
    }

    @Test
    void anElectionHasOneRestoreIncident() {
        final var world = SampleElection.fptp();
        final var first = this.filedOnce(world);
        final var failure = assertThrows(ConstraintException.class, () -> this.file(world, "Again", world.operator));
        final var payload = first.getPayload();
        assertTrue(payload.contains("\"reason\":\"Disk lost\""));
        assertEquals("An election has one restore incident", failure.getMessage());
    }

    @Test
    void incidentsStayPrivateUntilTheElectionIsActive() {
        final var world = SampleElection.upcomingFptp();
        final var id = world.electionId;
        final var failure = assertThrows(ConstraintException.class, () -> world.app.restoreIncidents(id));
        assertEquals("election is not public yet", failure.getMessage());
    }

    @Test
    void theReasonIsTrimmedInThePayload() {
        final var world = SampleElection.fptp();
        final var payload = world.app.restoreIncidentPayload(world.electionId, "  Disk lost  ", WHEN);
        assertTrue(payload.contains("\"reason\":\"Disk lost\""));
        assertTrue(payload.contains("\"witnessedHeads\":[]"));
    }

    private ConstraintException refused(final SampleElection world) {
        return assertThrows(ConstraintException.class, () -> this.file(world, "Disk lost", world.operator));
    }

    private ConstraintException refusedAfterGrowth(final SampleElection world) {
        RestoreFixture.castAndWitness(world);
        world.castOne(world.slotB, "Y");
        return this.refused(world);
    }

    private ConstraintException refusedUnwitnessed(final SampleElection world) {
        world.castOne(world.slotA, "Y");
        return this.refused(world);
    }

    private RestoreIncident filedOnce(final SampleElection world) {
        RestoreFixture.restoredAfterLoss(world);
        return this.file(world, "Disk lost", world.operator);
    }

    private VoteChain filedThenClosed(final SampleElection world) {
        this.file(world, "Disk lost", world.operator);
        world.close();
        return RestoreFixture.chain(world);
    }

    private String expectedPayload(final SampleElection world, final VoteChain chain, final String reason) {
        final var lost = new ChainHead(world.raceId, chain.getLength() + 1, RestoreFixture.LOST_HASH);
        final var restored = new ChainHead(world.raceId, chain.getLength(), chain.getHash());
        return Payloads.restoreIncident(
            world.electionId,
            List.of(lost),
            List.of(restored),
            reason,
            WHEN
        );
    }

    private RestoreIncident file(final SampleElection world, final String reason, final SigningKey key) {
        final var payload = world.app.restoreIncidentPayload(world.electionId, reason, WHEN);
        final var raw = Digests.unhex(Digests.sha256Hex(payload));
        final var signature = Signatures.sign(key.seed(), raw);
        return this.fileSigned(world, reason, signature);
    }

    private RestoreIncident fileSigned(final SampleElection world, final String reason, final String signature) {
        final var request = new RestoreRequest(reason, WHEN, signature);
        return world.app.fileRestoreIncident(world.electionId, request, world.actor);
    }

    private void undated(final SampleElection world) {
        final var request = new RestoreRequest("Disk lost", null, "00".repeat(64));
        world.app.fileRestoreIncident(world.electionId, request, world.actor);
    }
}
