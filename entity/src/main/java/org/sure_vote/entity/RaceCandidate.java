package org.sure_vote.entity;

import java.util.UUID;

public final class RaceCandidate extends TenantEntity {
    private UUID raceId;
    private UUID candidateId;
    private boolean writeIn;
    private ReferendumChoice referendumChoice;
    private boolean incumbent;
    private RaceCandidateStatus status;
    private int votesReceived;
    private Double votePercentage;
    private Integer rank;
    private Outcome outcome;

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

    public boolean isWriteIn() {
        return this.writeIn;
    }

    public void setWriteIn(final boolean writeIn) {
        this.writeIn = writeIn;
    }

    public ReferendumChoice getReferendumChoice() {
        return this.referendumChoice;
    }

    public void setReferendumChoice(final ReferendumChoice referendumChoice) {
        this.referendumChoice = referendumChoice;
    }

    public boolean isIncumbent() {
        return this.incumbent;
    }

    public void setIncumbent(final boolean incumbent) {
        this.incumbent = incumbent;
    }

    public RaceCandidateStatus getStatus() {
        return this.status;
    }

    public void setStatus(final RaceCandidateStatus status) {
        this.status = status;
    }

    public int getVotesReceived() {
        return this.votesReceived;
    }

    public void setVotesReceived(final int votesReceived) {
        this.votesReceived = votesReceived;
    }

    public Double getVotePercentage() {
        return this.votePercentage;
    }

    public void setVotePercentage(final Double votePercentage) {
        this.votePercentage = votePercentage;
    }

    public Integer getRank() {
        return this.rank;
    }

    public void setRank(final Integer rank) {
        this.rank = rank;
    }

    public Outcome getOutcome() {
        return this.outcome;
    }

    public void setOutcome(final Outcome outcome) {
        this.outcome = outcome;
    }
}
