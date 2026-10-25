package org.sure_vote.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.BallotType;
import org.sure_vote.entity.IssuanceStatus;
import org.sure_vote.entity.Outcome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ElectionFlowTest {
    @Test
    void showcaseRejectsSsnAndVoterId() {
        final var world = SampleElection.showcase();
        final var personId = world.person("Cara", "Three");
        this.assertMessage(world::secretPerson, "A showcase tenant rejects ssn and voter id");
        this.assertMessage(world::voterIdPerson, "A showcase tenant rejects ssn and voter id");
        assertNotNull(personId);
    }

    @Test
    void issuanceCanBeVoidedBeforeAMachineSession() {
        final var world = SampleElection.upcomingFptp();
        this.assertMessage(
            () -> world.accept(world.person("Vera", "Voter")),
            "Voters are accepted while the election is active"
        );
    }

    @Test
    void aVoidedIssuanceAllowsAnotherAcceptance() {
        final var world = SampleElection.fptp();
        final var voter = world.person("Vera", "Voter");
        final var first = world.accept(voter);
        final var second = world.reissue(voter, first);
        this.assertVoided(world, first, second);
    }

    @Test
    void aSecondAcceptedIssuanceIsRejected() {
        final var world = SampleElection.fptp();
        final var voter = world.person("Vera", "Voter");
        final var first = world.accept(voter);
        this.assertMessage(() -> world.accept(voter), "Voter already has an issued ballot");
        assertNotNull(first);
    }

    @Test
    void aBlankVoidReasonIsRejected() {
        final var world = SampleElection.fptp();
        final var voter = world.person("Vera", "Voter");
        final var first = world.accept(voter);
        this.assertMessage(
            () -> world.app.voidIssuance(first, "  ", world.actor),
            "A void reason is required"
        );
    }

    @Test
    void inPersonImportLinksTheChainAndLooksUpTheCode() {
        final var world = SampleElection.fptp();
        final var first = world.castOne(world.slotA, "Y");
        final var second = world.castOne(world.slotB, "Y");
        final var raw = AccessCodes.generate();
        final var missing = AccessCodes.encode(raw);
        world.assertImported(first, second);
        this.assertMessage(() -> world.app.lookup(world.electionId, missing), "no ballot for that code");
        this.assertMessage(() -> world.app.lookup(world.electionId, "not-a-code"), "no ballot for that code");
    }

    @Test
    void theCodeAloneOpensItsBallot() {
        final var world = SampleElection.fptp();
        final var ballot = world.castOne(world.slotA, "Y");
        final var code = world.lastCode;
        final var found = world.app.lookup(code, null);
        final var votes = found.votes();
        final var opened = found.ballot();
        final var missing = AccessCodes.encode(AccessCodes.generate());
        assertEquals(ballot.getId(), opened.getId());
        assertEquals(1, votes.size());
        this.assertMessage(() -> world.app.lookup(missing, null), "no ballot for that code");
        this.assertMessage(() -> world.app.election(UUID.randomUUID()), "Missing Election");
    }

    @Test
    void anEmptyBallotHasNoChainEntry() {
        final var world = SampleElection.fptp();
        final var ballot = world.cast(List.of());
        final var votes = world.repository.list(org.sure_vote.entity.Vote.class);
        final var entries = world.repository.list(org.sure_vote.entity.VoteChainEntry.class);
        final var chain = this.head(world);
        assertEquals(0, votes.size());
        assertEquals(0, entries.size());
        assertEquals(0, chain.getLength());
        assertEquals(Digests.sha256Hex(Payloads.emptyHead(world.raceId)), chain.getHash());
        this.assertEmptyHash(ballot);
    }

    @Test
    void mailInAndOnlineAreRejected() {
        final var world = SampleElection.fptp();
        final var mail = world.typed(BallotType.MAIL_IN);
        final var absentee = world.typed(BallotType.ABSENTEE);
        final var online = world.typed(BallotType.ONLINE);
        this.assertMessage(
            () -> world.app.importBallot(mail),
            "recording device kind does not match the ballot type"
        );
        this.assertMessage(
            () -> world.app.importBallot(absentee),
            "recording device kind does not match the ballot type"
        );
        this.assertMessage(() -> world.app.importBallot(online), "online ballots are not accepted");
    }

    @Test
    void rejectsABallotOutsideTheVotingWindow() {
        final var world = SampleElection.fptp();
        world.ballotTimestamp = Instant.parse("2026-10-01T00:00:00Z");
        this.assertMessage(
            () -> world.castOne(world.slotA, "Y"),
            "Ballot timestamp is outside the voting window"
        );
    }

    @Test
    void rejectsABadDeviceSignature() {
        final var world = SampleElection.fptp();
        final var sealed = world.sealed(world.slotA, "Y");
        final var broken = world.withSignature(sealed, "00");
        this.assertMessage(
            () -> world.app.importBallot(broken),
            "Device signature does not match the ballot"
        );
    }

    @Test
    void rejectsARaceOutsideTheBallotStyle() {
        final var world = SampleElection.unstyledRace();
        this.assertMessage(
            () -> world.castOne(world.slotB, "Y"),
            "The race is not in the precinct ballot style"
        );
    }

    @Test
    void primaryBallotCannotMixParties() {
        final var world = SampleElection.primary();
        final var left = new SealSelection(world.slotA, "Y", "n-red");
        final var right = new SealSelection(world.slotB, "Y", "n-blue");
        final var selections = List.of(left, right);
        this.assertMessage(() -> world.cast(selections), "Primary races on one ballot share one party");
    }

    @Test
    void firstPastThePostCountsAWriteInNameSeparately() {
        final var world = SampleElection.fptp();
        world.castOne(world.slotA, "Y");
        world.castOne(world.slotA, "Y");
        world.castOne(world.slotB, "Y");
        world.castOne(world.writeIn, "Pat Lee");
        world.close();
        world.assertOutcome(world.slotA, Outcome.WON, 1, 2);
        world.assertOutcome(world.slotB, Outcome.LOST, null, 1);
        world.assertOutcome(world.writeIn, null, null, 1);
        world.assertClosed();
    }

    @Test
    void aReferendumTieStaysTied() {
        final var world = SampleElection.referendum();
        world.castOne(world.slotA, "Y");
        world.castOne(world.slotB, "Y");
        world.close();
        world.assertOutcome(world.slotA, Outcome.TIED, null, 1);
        world.assertOutcome(world.slotB, Outcome.TIED, null, 1);
        world.assertClosed();
    }

    @Test
    void rankedChoiceElectsAFirstRoundMajority() {
        final var world = SampleElection.ranked();
        world.castRanked(List.of(world.slotA, world.slotB));
        world.castRanked(List.of(world.slotA, world.slotB));
        world.castRanked(List.of(world.slotB, world.slotA));
        world.close();
        world.assertOutcome(world.slotA, Outcome.WON, 1, 2);
        world.assertOutcome(world.slotB, Outcome.LOST, null, 1);
        world.assertClosed();
    }

    @Test
    void aRecordedBallotCannotBeReplaced() {
        final var world = SampleElection.fptp();
        final var ballot = world.castOne(world.slotA, "Y");
        final var failure = assertThrows(IllegalArgumentException.class, () -> world.repository.replace(ballot));
        assertEquals("Ballot is insert-only", failure.getMessage());
    }

    @Test
    void devicesLockAfterActivation() {
        final var world = SampleElection.fptp();
        final var device = world.device();
        this.assertMessage(
            () -> world.app.createRecordingDevice(device, world.actor),
            "Devices lock when an election is activated"
        );
    }

    private void assertVoided(final SampleElection world, final java.util.UUID first, final java.util.UUID second) {
        final var stored = world.repository.find(org.sure_vote.entity.BallotIssuance.class, first);
        final var voided = stored.orElseThrow();
        assertEquals(IssuanceStatus.VOIDED, voided.getStatus());
        assertEquals("machine not started", voided.getVoidReason());
        assertNotEquals(first, second);
    }

    private org.sure_vote.entity.VoteChain head(final SampleElection world) {
        final var chains = world.repository.list(org.sure_vote.entity.VoteChain.class);
        return chains.getFirst();
    }

    private void assertEmptyHash(final Ballot ballot) {
        final var type = ballot.getType();
        final var typeWire = type.wire();
        final var binding = Payloads.binding(
            ballot.getElectionId(),
            ballot.getVotingPrecinctId(),
            typeWire,
            ballot.getRecordingDeviceId(),
            ballot.getRecordingPlaceId(),
            ballot.getRecordedBy(),
            ballot.getTimestamp(),
            ballot.getVoterAccessHash()
        );
        assertEquals(binding, ballot.getHash());
    }

    private void assertMessage(final Executable work, final String message) {
        final var failure = assertThrows(ConstraintException.class, work);
        assertEquals(message, failure.getMessage());
    }
}
