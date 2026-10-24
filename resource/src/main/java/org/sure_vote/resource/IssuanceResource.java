package org.sure_vote.resource;

import java.util.UUID;

public final class IssuanceResource {
    private UUID id;
    private UUID tenantId;
    private UUID personId;
    private UUID electionId;
    private UUID recordingPlaceId;
    private String status;

    public UUID getId() {
        return this.id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return this.tenantId;
    }

    public void setTenantId(final UUID tenantId) {
        this.tenantId = tenantId;
    }

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

    public String getStatus() {
        return this.status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }
}
