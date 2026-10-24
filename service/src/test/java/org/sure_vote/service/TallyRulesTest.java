package org.sure_vote.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.sure_vote.entity.CanvassStatement;
import org.sure_vote.entity.Outcome;
import org.sure_vote.entity.Seat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TallyRulesTest {
    @Test
    void twoSeatsElectTheTopTwoWhenTheCutoffIsStrict() {
        final var world = this.seats(2);
        world.castOne(world.slotA, "Y");
        world.castOne(world.slotA, "Y");
        world.castOne(world.slotA, "Y");
        world.castOne(world.slotB, "Y");
        world.castOne(world.slotB, "Y");
        world.castOne(world.writeIn, "Pat Lee");
        world.close();
        world.assertOutcome(world.slotA, Outcome.WON, 1, 3);
        world.assertOutcome(world.slotB, Outcome.WON, 1, 2);
        world.assertOutcome(world.writeIn, null, null, 1);
    }

    @Test
    void aTieForTheLastSeatStaysTied() {
        final var world = this.seats(2);
        world.castOne(world.slotA, "Y");
        world.castOne(world.slotA, "Y");
        world.castOne(world.slotA, "Y");
        world.castOne(world.slotB, "Y");
        world.castOne(world.writeIn, "Pat Lee");
        world.close();
        world.assertOutcome(world.slotA, Outcome.WON, 1, 3);
        world.assertOutcome(world.slotB, Outcome.TIED, null, 1);
        world.assertOutcome(world.writeIn, null, null, 1);
    }

    @Test
    void rankedChoiceEliminatesTheLowestCandidate() {
        final var world = SampleElection.upcomingRanked();
        final var slotC = world.slot(world.raceId, "Cy", "Three", false, null, null);
        world.activate();
        world.castRanked(List.of(world.slotA, world.slotB, slotC));
        world.castRanked(List.of(world.slotA, world.slotB, slotC));
        world.castRanked(List.of(world.slotB, world.slotA, slotC));
        world.castRanked(List.of(world.slotB, world.slotA, slotC));
        world.castRanked(List.of(slotC, world.slotA, world.slotB));
        world.close();
        world.assertOutcome(world.slotA, Outcome.WON, 1, 2);
        world.assertOutcome(world.slotB, Outcome.LOST, null, 2);
        world.assertOutcome(slotC, Outcome.ELIMINATED, null, 1);
    }

    @Test
    void aRankedChoiceTieForLastPlaceStopsTheCount() {
        final var world = SampleElection.upcomingRanked();
        final var slotC = world.slot(world.raceId, "Cy", "Three", false, null, null);
        world.activate();
        world.castRanked(List.of(world.slotA, world.slotB, slotC));
        world.castRanked(List.of(world.slotA, slotC, world.slotB));
        world.castRanked(List.of(world.slotB, world.slotA, slotC));
        world.castRanked(List.of(slotC, world.slotA, world.slotB));
        world.close();
        world.assertOutcome(world.slotA, Outcome.LOST, null, 2);
        world.assertOutcome(world.slotB, Outcome.TIED, null, 1);
        world.assertOutcome(slotC, Outcome.TIED, null, 1);
    }

    @Test
    void closePublishesTheCanvassStatement() {
        final var world = SampleElection.fptp();
        final var payload = this.preview(world);
        world.close();
        this.assertCanvass(world, payload);
    }

    @Test
    void aBadCanvassSignatureLeavesTheElectionActive() {
        final var world = SampleElection.fptp();
        this.preview(world);
        this.rejectBadCanvass(world);
    }

    @Test
    void onlyAnActiveElectionCanClose() {
        final var world = SampleElection.upcomingFptp();
        final var signatures = Map.<UUID, String>of();
        this.assertMessage(
            () -> world.app.close(world.electionId, signatures, null, world.actor),
            "Only an active election can close"
        );
    }

    @Test
    void chainSignaturesAreRequired() {
        final var world = SampleElection.fptp();
        this.assertMessage(
            () -> world.app.close(world.electionId, null, null, world.actor),
            "chain signatures are required"
        );
    }

    @Test
    void eachChainNeedsAnOperatorSignature() {
        final var world = SampleElection.fptp();
        final var signatures = Map.<UUID, String>of();
        this.assertMessage(
            () -> world.app.close(world.electionId, signatures, null, world.actor),
            "Operator signature is required for each chain"
        );
    }

    @Test
    void aBadChainSignatureIsRejected() {
        final var world = SampleElection.fptp();
        this.assertMessage(
            () -> this.closeWith(world, "00".repeat(64)),
            "Operator signature does not match the chain head"
        );
    }

    private String preview(final SampleElection world) {
        world.castOne(world.slotA, "Y");
        return world.app.canvassPayload(world.electionId);
    }

    private void assertCanvass(final SampleElection world, final String payload) {
        final var statements = world.repository.list(CanvassStatement.class);
        final var statement = statements.getFirst();
        final var stored = statement.getPayload();
        final var hash = Digests.sha256Hex(stored);
        final var raw = Digests.unhex(hash);
        final var key = world.operator.publicKey();
        final var signed = Signatures.verify(key, raw, statement.getSignature());
        assertEquals(1, statements.size());
        assertEquals(payload, stored);
        assertEquals(hash, statement.getHash());
        assertEquals(world.electionId, statement.getElectionId());
        assertTrue(signed);
        assertTrue(stored.contains("\"ballots\":1"));
        assertTrue(stored.contains("\"undervotes\":0"));
        assertTrue(stored.contains("\"outcome\":\"won\""));
        assertTrue(stored.contains("\"acceptedIssuances\":0"));
    }

    private void rejectBadCanvass(final SampleElection world) {
        final var signatures = world.chainSignatures();
        final var signature = "00".repeat(64);
        final var message = "Operator signature does not match the canvass statement";
        this.assertMessage(
            () -> world.app.close(world.electionId, signatures, signature, world.actor),
            message
        );
        this.assertUnchanged(world);
    }

    private void assertUnchanged(final SampleElection world) {
        final var statements = world.repository.list(CanvassStatement.class);
        world.assertStillActive();
        assertEquals(0, statements.size());
    }

    private SampleElection seats(final int count) {
        final var world = SampleElection.upcomingFptp();
        this.appointments(world, count);
        world.activate();
        return world;
    }

    private void appointments(final SampleElection world, final int count) {
        final var found = world.repository.find(Seat.class, world.seatId);
        final var seat = found.orElseThrow();
        seat.setAppointments(count);
        world.repository.replace(seat);
    }

    private void closeWith(final SampleElection world, final String signature) {
        final var signatures = new LinkedHashMap<UUID, String>();
        signatures.put(world.raceId, signature);
        world.app.close(world.electionId, signatures, null, world.actor);
    }

    private void assertMessage(final Executable work, final String message) {
        final var failure = assertThrows(ConstraintException.class, work);
        assertEquals(message, failure.getMessage());
    }
}
