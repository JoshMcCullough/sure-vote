package org.sure_vote.service;

import java.time.Instant;
import java.text.Normalizer;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.sure_vote.entity.AssignmentStatus;
import org.sure_vote.entity.BallotStyle;
import org.sure_vote.entity.BallotType;
import org.sure_vote.entity.ChainStatus;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.ElectionMethod;
import org.sure_vote.entity.ElectionVotingPrecinctAssignment;
import org.sure_vote.entity.Race;
import org.sure_vote.entity.RaceType;
import org.sure_vote.entity.RecordingDevice;
import org.sure_vote.entity.RecordingDeviceStatus;
import org.sure_vote.entity.Vote;
import org.sure_vote.entity.VoteChain;
import org.sure_vote.entity.VotingPrecinct;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImportRulesTest {
    @Test
    void anUpcomingElectionRejectsABallot() {
        final var world = SampleElection.upcomingFptp();
        this.assertMessage(() -> world.castOne(world.slotA, "Y"), "Election is not active");
    }

    @Test
    void aCodeFromAnotherElectionDoesNotMatch() {
        final var world = SampleElection.fptp();
        final var ballot = world.castOne(world.slotA, "Y");
        final var code = world.lastCode;
        final var other = UUID.randomUUID();
        this.assertMessage(() -> world.app.lookup(other, code), "no ballot for that code");
        assertNotNull(ballot);
    }

    @Test
    void aBadAccessHashIsRejected() {
        final var world = SampleElection.fptp();
        final var sealed = world.sealed(world.slotA, "Y");
        final var broken = this.copy(sealed, sealed.recordedBy(), "ZZ", sealed.signature(), sealed.votes());
        this.assertMessage(
            () -> world.app.importBallot(broken),
            "voterAccessHash must be lowercase SHA-256 hex"
        );
    }

    @Test
    void votesAndRecordedByAreRequired() {
        final var world = SampleElection.fptp();
        final var sealed = world.sealed(world.slotA, "Y");
        final var noVotes = this.copy(sealed, sealed.recordedBy(), sealed.voterAccessHash(), sealed.signature(), null);
        final var noActor = this.copy(sealed, null, sealed.voterAccessHash(), sealed.signature(), sealed.votes());
        this.assertMessage(() -> world.app.importBallot(noVotes), "votes are required");
        this.assertMessage(() -> world.app.importBallot(noActor), "recordedBy is required");
    }

    @Test
    void aRetiredDeviceIsRejected() {
        final var world = SampleElection.fptp();
        this.deviceStatus(world, RecordingDeviceStatus.RETIRED, null);
        this.assertMessage(() -> world.castOne(world.slotA, "Y"), "Recording device is not active");
    }

    @Test
    void aDeletedDeviceIsRejected() {
        final var world = SampleElection.fptp();
        this.deviceStatus(world, RecordingDeviceStatus.ACTIVE, Instant.parse("2026-11-03T00:00:00Z"));
        this.assertMessage(() -> world.castOne(world.slotA, "Y"), "Recording device is deleted");
    }

    @Test
    void aChangedDeviceKeyBreaksTheSignedDefinition() {
        final var world = SampleElection.fptp();
        this.deviceKey(world, "cd".repeat(32));
        this.assertMessage(
            () -> world.castOne(world.slotA, "Y"),
            "Election definition does not match the signed hash"
        );
    }

    @Test
    void anUnassignedPrecinctIsRejected() {
        final var world = SampleElection.fptp();
        final var precinctId = this.extraPrecinct(world);
        final var sealed = this.sealedAt(world, precinctId, world.slotA, "Y");
        this.assertMessage(() -> world.app.importBallot(sealed), "Precinct is not assigned to the election");
    }

    @Test
    void aHistoricalAssignmentIsNotAnActivePrecinct() {
        final var world = SampleElection.fptp();
        this.assignmentStatus(world, AssignmentStatus.HISTORICAL);
        this.assertMessage(() -> world.castOne(world.slotA, "Y"), "Precinct is not assigned to the election");
    }

    @Test
    void aDeletedBallotStyleBreaksTheSignedDefinition() {
        final var world = SampleElection.fptp();
        this.deleteStyle(world);
        this.assertMessage(
            () -> world.castOne(world.slotA, "Y"),
            "Election definition does not match the signed hash"
        );
    }

    @Test
    void aVoteForAnotherElectionIsRejected() {
        final var world = SampleElection.fptp();
        final var slot = this.slotInAnotherElection(world);
        final var sealed = this.sealedAt(world, world.precinctId, slot, "Y");
        this.assertMessage(() -> world.app.importBallot(sealed), "The race is in another election");
    }

    @Test
    void aDeletedRaceCandidateBreaksTheSignedDefinition() {
        final var world = SampleElection.fptp();
        this.deleteSlot(world, world.slotA);
        this.assertMessage(
            () -> world.castOne(world.slotA, "Y"),
            "Election definition does not match the signed hash"
        );
    }

    @Test
    void voteFieldsAreRequired() {
        final var world = SampleElection.fptp();
        final var sealed = world.sealed(world.slotA, "Y");
        final var votes = sealed.votes();
        final var vote = votes.getFirst();
        final var timestamp = vote.entryTimestamp();
        final var longNonce = "n".repeat(65);
        final var noSignature = this.oneVote(sealed, null, timestamp, "n-1", "ab");
        final var noEntrySignature = this.oneVote(sealed, "ab", timestamp, "n-1", null);
        final var noTimestamp = this.oneVote(sealed, "ab", null, "n-1", "ab");
        final var noNonce = this.oneVote(sealed, "ab", timestamp, longNonce, "ab");
        this.assertMessage(() -> world.app.importBallot(noSignature), "vote signature is required");
        this.assertMessage(() -> world.app.importBallot(noEntrySignature), "entry signature is required");
        this.assertMessage(() -> world.app.importBallot(noTimestamp), "entry timestamp is required");
        this.assertMessage(() -> world.app.importBallot(noNonce), "nonce must be 1 to 64 characters");
    }

    @Test
    void aBallotVotesOnceForACandidate() {
        final var world = SampleElection.fptp();
        final var left = new SealSelection(world.slotA, "Y", "n-1");
        final var right = new SealSelection(world.slotA, "Y", "n-2");
        final var selections = List.of(left, right);
        this.assertMessage(() -> world.cast(selections), "A ballot votes once for a candidate");
    }

    @Test
    void aFirstPastThePostRaceAllowsOneVote() {
        final var world = SampleElection.fptp();
        final var left = new SealSelection(world.slotA, "Y", "n-1");
        final var right = new SealSelection(world.slotB, "Y", "n-2");
        final var selections = List.of(left, right);
        this.assertMessage(() -> world.cast(selections), "A first-past-the-post race allows one vote");
    }

    @Test
    void aFirstPastThePostVoteUsesY() {
        final var world = SampleElection.fptp();
        this.assertMessage(() -> world.castOne(world.slotA, "N"), "A first-past-the-post vote uses Y");
    }

    @Test
    void aWriteInNameIsRequired() {
        final var world = SampleElection.fptp();
        final var sealed = world.sealed(world.writeIn, "Pat");
        final var broken = this.value(sealed, null);
        this.assertMessage(() -> world.app.importBallot(broken), "A write-in name is required");
    }

    @Test
    void aWriteInNameIsChecked() {
        this.assertWriteIn(Normalizer.normalize("\u00e9", Normalizer.Form.NFD), "A write-in name is Unicode NFC");
        this.assertWriteIn(" Pat", "A write-in name has no extra whitespace");
        this.assertWriteIn("a".repeat(81), "A write-in name is 1 to 80 characters");
        this.assertWriteIn("A\u0001", "A write-in name has no control characters");
    }

    @Test
    void aReferendumAllowsOneVoteAndUsesY() {
        final var world = SampleElection.referendum();
        final var left = new SealSelection(world.slotA, "Y", "n-1");
        final var right = new SealSelection(world.slotB, "Y", "n-2");
        final var selections = List.of(left, right);
        this.assertMessage(() -> world.cast(selections), "A referendum allows one vote");
        this.assertMessage(() -> world.castOne(world.slotA, "N"), "A referendum vote uses Y");
    }

    @Test
    void rankedChoiceRanksAreContiguous() {
        final var world = SampleElection.ranked();
        final var first = new SealSelection(world.slotA, "1", "n-1");
        final var skipped = new SealSelection(world.slotB, "3", "n-2");
        final var pair = List.of(first, skipped);
        final var ballot = world.cast(List.of(first, new SealSelection(world.slotB, "2", "n-2")));
        final var votes = world.repository.list(Vote.class);
        this.assertMessage(() -> world.cast(pair), "Ranked-choice ranks are contiguous from 1");
        this.assertMessage(() -> world.castOne(world.slotA, "4"), "Ranked-choice ranks are 1, 2, or 3");
        assertEquals(2, votes.size());
        assertNotNull(ballot);
    }

    @Test
    void aBadVoteSignatureIsRejected() {
        final var world = SampleElection.fptp();
        final var sealed = world.sealed(world.slotA, "Y");
        final var votes = sealed.votes();
        final var vote = votes.getFirst();
        final var timestamp = vote.entryTimestamp();
        final var nonce = vote.nonce();
        final var entrySignature = vote.entrySignature();
        final var broken = this.oneVote(sealed, "00".repeat(64), timestamp, nonce, entrySignature);
        this.assertMessage(() -> world.app.importBallot(broken), "Device signature does not match a vote");
    }

    @Test
    void aBadEntrySignatureIsRejected() {
        final var world = SampleElection.fptp();
        final var sealed = world.sealed(world.slotA, "Y");
        final var votes = sealed.votes();
        final var vote = votes.getFirst();
        final var signature = vote.signature();
        final var timestamp = vote.entryTimestamp();
        final var nonce = vote.nonce();
        final var broken = this.oneVote(sealed, signature, timestamp, nonce, "00".repeat(64));
        this.assertMessage(() -> world.app.importBallot(broken), "Import signature does not match the chain entry");
    }

    @Test
    void aClosedChainRejectsAnotherBallot() {
        final var world = SampleElection.fptp();
        this.closeChain(world);
        this.assertMessage(() -> world.castOne(world.slotA, "Y"), "Vote chain is not active");
    }

    private void assertWriteIn(final String value, final String message) {
        final var world = SampleElection.fptp();
        this.assertMessage(() -> world.castOne(world.writeIn, value), message);
    }

    private void assertMessage(final Executable work, final String message) {
        final var failure = assertThrows(ConstraintException.class, work);
        assertEquals(message, failure.getMessage());
    }

    private ImportBallot copy(
        final ImportBallot sealed,
        final UUID recordedBy,
        final String hash,
        final String signature,
        final List<ImportVote> votes
    ) {
        return new ImportBallot(
            sealed.tenantId(),
            sealed.actorId(),
            sealed.electionId(),
            sealed.votingPrecinctId(),
            sealed.type(),
            sealed.recordingDeviceId(),
            sealed.recordingPlaceId(),
            recordedBy,
            sealed.timestamp(),
            hash,
            signature,
            votes
        );
    }

    private ImportBallot oneVote(
        final ImportBallot sealed,
        final String signature,
        final Instant entryTimestamp,
        final String nonce,
        final String entrySignature
    ) {
        final var votes = sealed.votes();
        final var vote = votes.getFirst();
        final var replaced = new ImportVote(
            vote.raceCandidateId(),
            vote.value(),
            signature,
            entryTimestamp,
            nonce,
            entrySignature
        );
        return this.copy(sealed, sealed.recordedBy(), sealed.voterAccessHash(), sealed.signature(), List.of(replaced));
    }

    private ImportBallot value(final ImportBallot sealed, final String value) {
        final var votes = sealed.votes();
        final var vote = votes.getFirst();
        final var replaced = new ImportVote(
            vote.raceCandidateId(),
            value,
            vote.signature(),
            vote.entryTimestamp(),
            vote.nonce(),
            vote.entrySignature()
        );
        return this.copy(sealed, sealed.recordedBy(), sealed.voterAccessHash(), sealed.signature(), List.of(replaced));
    }

    private void deviceStatus(
        final SampleElection world,
        final RecordingDeviceStatus status,
        final Instant deletedAt
    ) {
        final var found = world.repository.find(RecordingDevice.class, world.deviceId);
        final var device = found.orElseThrow();
        device.setStatus(status);
        device.setDeletedAt(deletedAt);
        world.repository.replace(device);
    }

    private void deviceKey(final SampleElection world, final String key) {
        final var found = world.repository.find(RecordingDevice.class, world.deviceId);
        final var device = found.orElseThrow();
        device.setPublicKey(key);
        world.repository.replace(device);
    }

    private UUID extraPrecinct(final SampleElection world) {
        final var precinct = this.precinct(world);
        final var saved = world.app.createPrecinct(precinct, world.actor);
        return saved.getId();
    }

    private VotingPrecinct precinct(final SampleElection world) {
        final var precinct = new VotingPrecinct();
        precinct.setTenantId(world.tenantId);
        precinct.setName("Precinct 2");
        precinct.setAddressId(world.addressId);
        return precinct;
    }

    private ImportBallot sealedAt(
        final SampleElection world,
        final UUID precinctId,
        final UUID slotId,
        final String value
    ) {
        final var raw = AccessCodes.generate();
        final var hash = AccessCodes.inPersonHash(raw);
        final var selection = new SealSelection(slotId, value, "n-extra");
        final var request = new SealRequest(
            world.tenantId,
            world.actor,
            world.electionId,
            precinctId,
            BallotType.IN_PERSON,
            world.deviceId,
            world.placeId,
            world.deviceId,
            world.ballotTimestamp,
            hash,
            world.entryTimestamp,
            List.of(selection)
        );
        return Sealer.seal(world.repository, request, world.deviceKey, world.importKey);
    }

    private void assignmentStatus(final SampleElection world, final AssignmentStatus status) {
        final var rows = world.repository.list(ElectionVotingPrecinctAssignment.class);
        final var row = rows.getFirst();
        row.setAssignmentStatus(status);
        world.repository.replace(row);
    }

    private void deleteStyle(final SampleElection world) {
        final var rows = world.repository.list(BallotStyle.class);
        final var row = rows.getFirst();
        row.setDeletedAt(Instant.parse("2026-11-03T00:00:00Z"));
        world.repository.replace(row);
    }

    private UUID slotInAnotherElection(final SampleElection world) {
        final var electionId = this.otherElection(world);
        final var raceId = this.otherRace(world, electionId);
        return world.slot(raceId, "Cara", "Three", false, null, null);
    }

    private UUID otherElection(final SampleElection world) {
        final var election = this.specialElection(world);
        final var saved = world.app.createElection(election, world.actor);
        return saved.getId();
    }

    private Election specialElection(final SampleElection world) {
        final var election = new Election();
        final var operatorKey = world.operator.publicKey();
        final var importKey = world.importKey.publicKey();
        election.setTenantId(world.tenantId);
        election.setLocalityId(world.localityId);
        election.setName("Special");
        election.setVotingStart(Instant.parse("2026-11-03T00:00:00Z"));
        election.setVotingEnd(Instant.parse("2026-11-04T00:00:00Z"));
        election.setOperatorPublicKey(operatorKey);
        election.setImportPublicKey(importKey);
        election.setViewingPublicKey("ab".repeat(32));
        return election;
    }

    private UUID otherRace(final SampleElection world, final UUID electionId) {
        final var race = this.specialRace(world, electionId);
        final var created = world.app.createRace(race, world.actor);
        return created.getId();
    }

    private Race specialRace(final SampleElection world, final UUID electionId) {
        final var race = new Race();
        race.setTenantId(world.tenantId);
        race.setElectionId(electionId);
        race.setTermId(world.termId);
        race.setName("Other");
        race.setMethod(ElectionMethod.FIRST_PAST_THE_POST);
        race.setType(RaceType.GENERAL);
        return race;
    }

    private void deleteSlot(final SampleElection world, final UUID slotId) {
        final var found = world.repository.find(org.sure_vote.entity.RaceCandidate.class, slotId);
        final var slot = found.orElseThrow();
        final var next = Copies.raceCandidate(slot);
        next.setDeletedAt(Instant.parse("2026-11-03T00:00:00Z"));
        world.repository.replace(next);
    }

    private void closeChain(final SampleElection world) {
        final var rows = world.repository.list(VoteChain.class);
        final var chain = rows.getFirst();
        final var next = Copies.chain(chain);
        next.setStatus(ChainStatus.CLOSED);
        world.repository.replace(next);
    }
}
