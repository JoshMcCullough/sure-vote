package org.sure_vote.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.sure_vote.entity.Outcome;
import org.sure_vote.entity.Seat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void onlyAnActiveElectionCanClose() {
        final var world = SampleElection.upcomingFptp();
        final var signatures = Map.<UUID, String>of();
        this.assertMessage(
            () -> world.app.close(world.electionId, signatures, world.actor),
            "Only an active election can close"
        );
    }

    @Test
    void chainSignaturesAreRequired() {
        final var world = SampleElection.fptp();
        this.assertMessage(
            () -> world.app.close(world.electionId, null, world.actor),
            "chain signatures are required"
        );
    }

    @Test
    void eachChainNeedsAnOperatorSignature() {
        final var world = SampleElection.fptp();
        final var signatures = Map.<UUID, String>of();
        this.assertMessage(
            () -> world.app.close(world.electionId, signatures, world.actor),
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
        world.app.close(world.electionId, signatures, world.actor);
    }

    private void assertMessage(final Executable work, final String message) {
        final var failure = assertThrows(ConstraintException.class, work);
        assertEquals(message, failure.getMessage());
    }
}
