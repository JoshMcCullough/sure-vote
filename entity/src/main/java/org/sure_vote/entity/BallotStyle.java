package org.sure_vote.entity;

import java.util.UUID;

public final class BallotStyle extends TenantEntity {
    private UUID electionId;
    private UUID votingPrecinctId;
    private UUID raceId;

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
