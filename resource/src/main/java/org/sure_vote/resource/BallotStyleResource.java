package org.sure_vote.resource;

import java.util.UUID;

public final class BallotStyleResource {
    private UUID id;

    private UUID tenantId;

    private UUID electionId;

    private UUID votingPrecinctId;

    private UUID raceId;

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

    public UUID getRaceId() {
        return this.raceId;
    }

    public void setRaceId(final UUID raceId) {
        this.raceId = raceId;
    }
}
