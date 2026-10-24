package org.sure_vote.service;

import java.time.Instant;
import java.util.UUID;

import org.sure_vote.entity.ElectionMethod;
import org.sure_vote.entity.RaceType;
import org.sure_vote.entity.ReferendumChoice;

final class PreparedVote {
    private final UUID raceId;
    private final UUID raceCandidateId;
    private final String value;
    private final int rank;
    private final int appointments;
    private final ElectionMethod method;
    private final RaceType raceType;
    private final String party;
    private final boolean writeIn;
    private final ReferendumChoice referendumChoice;
    private final String signature;
    private final Instant entryTimestamp;
    private final String nonce;
    private final String entrySignature;
    private final String payload;
    private final String hash;

    PreparedVote(
        final UUID raceId,
        final UUID raceCandidateId,
        final String value,
        final int rank,
        final int appointments,
        final ElectionMethod method,
        final RaceType raceType,
        final String party,
        final boolean writeIn,
        final ReferendumChoice referendumChoice,
        final String signature,
        final Instant entryTimestamp,
        final String nonce,
        final String entrySignature,
        final String payload,
        final String hash
    ) {
        this.raceId = raceId;
        this.raceCandidateId = raceCandidateId;
        this.value = value;
        this.rank = rank;
        this.appointments = appointments;
        this.method = method;
        this.raceType = raceType;
        this.party = party;
        this.writeIn = writeIn;
        this.referendumChoice = referendumChoice;
        this.signature = signature;
        this.entryTimestamp = entryTimestamp;
        this.nonce = nonce;
        this.entrySignature = entrySignature;
        this.payload = payload;
        this.hash = hash;
    }

    PreparedVote withHash(final String binding) {
        final var votePayload = Payloads.vote(binding, this.raceCandidateId, this.value);
        final var voteHash = Digests.sha256Hex(votePayload);
        return new PreparedVote(
            this.raceId,
            this.raceCandidateId,
            this.value,
            this.rank,
            this.appointments,
            this.method,
            this.raceType,
            this.party,
            this.writeIn,
            this.referendumChoice,
            this.signature,
            this.entryTimestamp,
            this.nonce,
            this.entrySignature,
            votePayload,
            voteHash
        );
    }

    static int order(final PreparedVote left, final PreparedVote right) {
        return VoteOrder.compare(
            left.raceId,
            left.rank,
            left.raceCandidateId,
            right.raceId,
            right.rank,
            right.raceCandidateId
        );
    }

    UUID raceId() {
        return this.raceId;
    }

    UUID raceCandidateId() {
        return this.raceCandidateId;
    }

    String value() {
        return this.value;
    }

    int rank() {
        return this.rank;
    }

    int appointments() {
        return this.appointments;
    }

    ElectionMethod method() {
        return this.method;
    }

    RaceType raceType() {
        return this.raceType;
    }

    String party() {
        return this.party;
    }

    boolean writeIn() {
        return this.writeIn;
    }

    ReferendumChoice referendumChoice() {
        return this.referendumChoice;
    }

    String signature() {
        return this.signature;
    }

    Instant entryTimestamp() {
        return this.entryTimestamp;
    }

    String nonce() {
        return this.nonce;
    }

    String entrySignature() {
        return this.entrySignature;
    }

    String payload() {
        return this.payload;
    }

    String hash() {
        return this.hash;
    }
}
