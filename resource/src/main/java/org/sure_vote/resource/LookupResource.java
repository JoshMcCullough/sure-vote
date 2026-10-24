package org.sure_vote.resource;

import java.util.List;

public final class LookupResource {
    private BallotResource ballot;
    private List<VoteResource> votes;

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
