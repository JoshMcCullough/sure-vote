package org.sure_vote.service;

import java.util.List;

import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.Vote;

public final class LookupResult {
    private final Ballot ballot;
    private final List<Vote> votes;

    public LookupResult(final Ballot ballot, final List<Vote> votes) {
        this.ballot = ballot;
        this.votes = votes;
    }

    public Ballot ballot() {
        return this.ballot;
    }

    public List<Vote> votes() {
        return this.votes;
    }
}
