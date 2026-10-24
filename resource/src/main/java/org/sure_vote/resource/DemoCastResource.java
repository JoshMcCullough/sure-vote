package org.sure_vote.resource;

import java.util.List;

public final class DemoCastResource {
    private String code;
    private BallotResource ballot;
    private List<VoteResource> votes;

    public String getCode() {
        return this.code;
    }

    public void setCode(final String code) {
        this.code = code;
    }

    public BallotResource getBallot() {
        return this.ballot;
    }

    public void setBallot(final BallotResource ballot) {
        this.ballot = ballot;
    }

    public List<VoteResource> getVotes() {
        return this.votes;
    }

    public void setVotes(final List<VoteResource> votes) {
        this.votes = votes;
    }
}
