package org.sure_vote.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.sure_vote.entity.BallotType;

public final class ImportBallot {
    private final UUID tenantId;
    private final UUID actorId;
    private final UUID electionId;
    private final UUID votingPrecinctId;
    private final BallotType type;
    private final UUID recordingDeviceId;
    private final UUID recordingPlaceId;
    private final UUID recordedBy;
    private final Instant timestamp;
    private final String voterAccessHash;
    private final String signature;
    private final List<ImportVote> votes;
    private final UUID personId;

    public ImportBallot(
        final UUID tenantId,
        final UUID actorId,
        final UUID electionId,
        final UUID votingPrecinctId,
        final BallotType type,
        final UUID recordingDeviceId,
        final UUID recordingPlaceId,
        final UUID recordedBy,
        final Instant timestamp,
        final String voterAccessHash,
        final String signature,
        final List<ImportVote> votes
    ) {
        this(
            tenantId,
            actorId,
            electionId,
            votingPrecinctId,
            type,
            recordingDeviceId,
            recordingPlaceId,
            recordedBy,
            timestamp,
            voterAccessHash,
            signature,
            votes,
            null
        );
    }

    public ImportBallot(
        final UUID tenantId,
        final UUID actorId,
        final UUID electionId,
        final UUID votingPrecinctId,
        final BallotType type,
        final UUID recordingDeviceId,
        final UUID recordingPlaceId,
        final UUID recordedBy,
        final Instant timestamp,
        final String voterAccessHash,
        final String signature,
        final List<ImportVote> votes,
        final UUID personId
    ) {
        this.tenantId = tenantId;
        this.actorId = actorId;
        this.electionId = electionId;
        this.votingPrecinctId = votingPrecinctId;
        this.type = type;
        this.recordingDeviceId = recordingDeviceId;
        this.recordingPlaceId = recordingPlaceId;
        this.recordedBy = recordedBy;
        this.timestamp = timestamp;
        this.voterAccessHash = voterAccessHash;
        this.signature = signature;
        this.votes = votes;
        this.personId = personId;
    }

    public ImportBallot withPerson(final UUID personId) {
        return new ImportBallot(
            this.tenantId,
            this.actorId,
            this.electionId,
            this.votingPrecinctId,
            this.type,
            this.recordingDeviceId,
            this.recordingPlaceId,
            this.recordedBy,
            this.timestamp,
            this.voterAccessHash,
            this.signature,
            this.votes,
            personId
        );
    }

    public UUID tenantId() {
        return this.tenantId;
    }

    public UUID actorId() {
        return this.actorId;
    }

    public UUID electionId() {
        return this.electionId;
    }

    public UUID votingPrecinctId() {
        return this.votingPrecinctId;
    }

    public BallotType type() {
        return this.type;
    }

    public UUID recordingDeviceId() {
        return this.recordingDeviceId;
    }

    public UUID recordingPlaceId() {
        return this.recordingPlaceId;
    }

    public UUID recordedBy() {
        return this.recordedBy;
    }

    public Instant timestamp() {
        return this.timestamp;
    }

    public String voterAccessHash() {
        return this.voterAccessHash;
    }

    public String signature() {
        return this.signature;
    }

    public List<ImportVote> votes() {
        return this.votes;
    }

    public UUID personId() {
        return this.personId;
    }
}
