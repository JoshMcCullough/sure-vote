package org.sure_vote.entity;

import java.time.Instant;
import java.util.UUID;

public final class BallotIssuance extends TenantEntity {
    private UUID personId;
    private UUID electionId;
    private UUID recordingPlaceId;
    private Instant acceptedAt;
    private IssuanceStatus status;
    private String voidReason;
    private Instant receivedAt;

    public UUID getPersonId() {
        return this.personId;
    }

    public void setPersonId(final UUID personId) {
        this.personId = personId;
    }

    public UUID getElectionId() {
        return this.electionId;
    }

    public void setElectionId(final UUID electionId) {
        this.electionId = electionId;
    }

    public UUID getRecordingPlaceId() {
        return this.recordingPlaceId;
    }

    public void setRecordingPlaceId(final UUID recordingPlaceId) {
        this.recordingPlaceId = recordingPlaceId;
    }

    public Instant getAcceptedAt() {
        return this.acceptedAt;
    }

    public void setAcceptedAt(final Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public IssuanceStatus getStatus() {
        return this.status;
    }

    public void setStatus(final IssuanceStatus status) {
        this.status = status;
    }

    public String getVoidReason() {
        return this.voidReason;
    }

    public void setVoidReason(final String voidReason) {
        this.voidReason = voidReason;
    }

    public Instant getReceivedAt() {
        return this.receivedAt;
    }

    public void setReceivedAt(final Instant receivedAt) {
        this.receivedAt = receivedAt;
    }
}
