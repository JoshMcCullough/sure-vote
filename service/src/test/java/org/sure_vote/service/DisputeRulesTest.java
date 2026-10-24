package org.sure_vote.service;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.DisputeStatus;
import org.sure_vote.entity.Outcome;
import org.sure_vote.entity.VoteChain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DisputeRulesTest {
    @Test
    void aFiledDisputeDoesNotChangeTheBallotOrTheTally() {
        final var world = SampleElection.fptp();
        final var ballot = world.castOne(world.slotA, "Y");
        final var code = world.codes.get(ballot.getId());
        final var hash = ballot.getHash();
        final var chain = this.chain(world);
        final var chainHash = chain.getHash();
        final var filed = world.app.fileDispute(
            world.electionId,
            code,
            null,
            "the receipt shows a different choice"
        );
        final var noted = world.app.setDisputeStatus(filed.getId(), DisputeStatus.NOTED, world.actor);
        final var again = this.ballotAfterClose(world, ballot.getId());
        final var closed = this.chain(world);
        assertEquals(DisputeStatus.OPEN, filed.getStatus());
        assertEquals("the receipt shows a different choice", filed.getStatement());
        assertNull(filed.getCreatedBy());
        assertEquals(DisputeStatus.NOTED, noted.getStatus());
        assertEquals(hash, again.getHash());
        assertEquals(chainHash, closed.getHash());
        world.assertOutcome(world.slotA, Outcome.WON, 1, 1);
    }

    @Test
    void theVoterReadsTheDisputeWithTheSameCode() {
        final var world = SampleElection.fptp();
        final var ballot = world.castOne(world.slotA, "Y");
        final var code = world.codes.get(ballot.getId());
        final var blank = world.app.fileDispute(world.electionId, code, null, "  ");
        final var second = world.app.fileDispute(world.electionId, code, null, "second claim");
        final var rows = world.app.disputesForCode(world.electionId, code, null);
        final var first = rows.get(0);
        final var count = world.app.disputeCount(world.electionId);
        assertEquals(2, rows.size());
        assertNull(blank.getStatement());
        assertEquals("second claim", second.getStatement());
        assertNull(first.getStatement());
        assertEquals(2, count);
    }

    @Test
    void aMissingCodeIsTheSameMissAsLookup() {
        final var world = SampleElection.fptp();
        final var failure = assertThrows(
            ConstraintException.class,
            () -> world.app.fileDispute(world.electionId, "not-a-code", null, "claim")
        );
        assertEquals("no ballot for that code", failure.getMessage());
    }

    @Test
    void anOfficialListRequiresAnActor() {
        final var world = SampleElection.fptp();
        final var failure = assertThrows(
            ConstraintException.class,
            () -> world.app.listDisputes(world.electionId, null)
        );
        assertEquals("actor is required", failure.getMessage());
    }

    @Test
    void anOpenStatusIsNotAReview() {
        final var world = SampleElection.fptp();
        final var ballot = world.castOne(world.slotA, "Y");
        final var code = world.codes.get(ballot.getId());
        final var filed = world.app.fileDispute(world.electionId, code, null, "claim");
        final var failure = assertThrows(
            ConstraintException.class,
            () -> world.app.setDisputeStatus(filed.getId(), DisputeStatus.OPEN, world.actor)
        );
        assertEquals("dispute status must be dismissed or noted", failure.getMessage());
    }

    @Test
    void aMissingDisputeIsRejected() {
        final var world = SampleElection.fptp();
        final var failure = assertThrows(
            ConstraintException.class,
            () -> world.app.setDisputeStatus(UUID.randomUUID(), DisputeStatus.DISMISSED, world.actor)
        );
        assertEquals("Missing BallotDispute", failure.getMessage());
    }

    private VoteChain chain(final SampleElection world) {
        final var chains = world.repository.list(VoteChain.class);
        return chains.getFirst();
    }

    private Ballot ballotAfterClose(final SampleElection world, final UUID ballotId) {
        world.close();
        return this.requireBallot(world, ballotId);
    }

    private Ballot requireBallot(final SampleElection world, final UUID ballotId) {
        final var stored = world.repository.find(Ballot.class, ballotId);
        return stored.orElseThrow();
    }
}
