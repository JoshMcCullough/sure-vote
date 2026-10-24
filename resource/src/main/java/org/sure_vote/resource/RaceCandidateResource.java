package org.sure_vote.resource;

import java.util.UUID;

public final class RaceCandidateResource {
    private UUID id;

    private UUID tenantId;

    private UUID raceId;

    private UUID candidateId;

    private boolean writeIn;

    private String referendumChoice;

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

    public UUID getRaceId() {
        return this.raceId;
    }

    public void setRaceId(final UUID raceId) {
        this.raceId = raceId;
    }

    public UUID getCandidateId() {
        return this.candidateId;
    }

    public void setCandidateId(final UUID candidateId) {
        this.candidateId = candidateId;
    }

    public boolean getWriteIn() {
        return this.writeIn;
    }

    public void setWriteIn(final boolean writeIn) {
        this.writeIn = writeIn;
    }

    public String getReferendumChoice() {
        return this.referendumChoice;
    }

    public void setReferendumChoice(final String referendumChoice) {
        this.referendumChoice = referendumChoice;
    }
}
