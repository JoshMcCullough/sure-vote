package org.sure_vote.entity;

import java.time.Instant;
import java.util.UUID;

public final class Ballot extends LedgerEntity {
    private UUID electionId;
    private UUID votingPrecinctId;
    private BallotType type;
    private UUID recordingDeviceId;
    private UUID recordingPlaceId;
    private UUID recordedBy;
    private String voterAccessHash;
    private String payload;
    private String hash;
    private String signature;
    private Instant timestamp;

    public UUID getElectionId() {
        return this.electionId;
    }

    public void setElectionId(final UUID electionId) {
        this.electionId = electionId;
    }

    public UUID getVotingPrecinctId() {
        return this.votingPrecinctId;
    }

    public void setVotingPrecinctId(final UUID votingPrecinctId) {
        this.votingPrecinctId = votingPrecinctId;
    }

    public BallotType getType() {
        return this.type;
    }

    public void setType(final BallotType type) {
        this.type = type;
    }

    public UUID getRecordingDeviceId() {
        return this.recordingDeviceId;
    }

    public void setRecordingDeviceId(final UUID recordingDeviceId) {
        this.recordingDeviceId = recordingDeviceId;
    }

    public UUID getRecordingPlaceId() {
        return this.recordingPlaceId;
    }

    public void setRecordingPlaceId(final UUID recordingPlaceId) {
        this.recordingPlaceId = recordingPlaceId;
    }

    public UUID getRecordedBy() {
        return this.recordedBy;
    }

    public void setRecordedBy(final UUID recordedBy) {
        this.recordedBy = recordedBy;
    }

    public String getVoterAccessHash() {
        return this.voterAccessHash;
    }

    public void setVoterAccessHash(final String voterAccessHash) {
        this.voterAccessHash = voterAccessHash;
    }

    public String getPayload() {
        return this.payload;
    }

    public void setPayload(final String payload) {
        this.payload = payload;
    }

    public String getHash() {
        return this.hash;
    }

    public void setHash(final String hash) {
        this.hash = hash;
    }

    public String getSignature() {
        return this.signature;
    }

    public void setSignature(final String signature) {
        this.signature = signature;
    }

    public Instant getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(final Instant timestamp) {
        this.timestamp = timestamp;
    }
}
