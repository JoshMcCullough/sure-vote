package org.sure_vote.resource;

import java.util.UUID;

public final class BallotResource {
    private UUID id;
    private UUID electionId;
    private UUID votingPrecinctId;
    private String type;
    private UUID recordingDeviceId;
    private UUID recordingPlaceId;
    private UUID recordedBy;
    private String timestamp;
    private String hash;
    private String payload;

    public UUID getId() {
        return this.id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

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

    public String getType() {
        return this.type;
    }

    public void setType(final String type) {
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

    public String getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(final String timestamp) {
        this.timestamp = timestamp;
    }

    public String getHash() {
        return this.hash;
    }

    public void setHash(final String hash) {
        this.hash = hash;
    }

    public String getPayload() {
        return this.payload;
    }

    public void setPayload(final String payload) {
        this.payload = payload;
    }
}
