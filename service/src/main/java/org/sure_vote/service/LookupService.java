package org.sure_vote.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.Vote;
import org.sure_vote.repository.SureVoteRepository;

final class LookupService {
    private final SureVoteRepository repository;
    private final Stores stores;

    LookupService(final SureVoteRepository repository, final Stores stores) {
        this.repository = repository;
        this.stores = stores;
    }

    LookupResult lookup(final UUID electionId, final String code) {
        final var raw = this.decode(code);
        final var hash = AccessCodes.inPersonHash(raw);
        final var ballot = this.find(electionId, hash);
        final var votes = this.votesFor(ballot.getId());
        return new LookupResult(ballot, votes);
    }

    private byte[] decode(final String code) {
        try {
            return this.decoded(code);
        }
        catch (final IllegalArgumentException failure) {
            throw new ConstraintException("no ballot for that code");
        }
    }

    private byte[] decoded(final String code) {
        if (code == null) {
            throw new IllegalArgumentException("missing code");
        }
        else {
            final var raw = AccessCodes.decode(code);
            this.stores.require(raw.length == 32, "no ballot for that code");
            return raw;
        }
    }

    private Ballot find(final UUID electionId, final String hash) {
        final var matched = new ArrayList<Ballot>();
        final var ballots = this.repository.list(Ballot.class);
        for (final var ballot : ballots) {
            this.collectBallot(matched, ballot, electionId, hash);
        }
        this.stores.require(!matched.isEmpty(), "no ballot for that code");
        return matched.getFirst();
    }

    private void collectBallot(
        final List<Ballot> matched,
        final Ballot ballot,
        final UUID electionId,
        final String hash
    ) {
        final var ballotElection = ballot.getElectionId();
        final var accessHash = ballot.getVoterAccessHash();
        final var matches = electionId.equals(ballotElection) && hash.equals(accessHash);
        if (matches) {
            matched.add(ballot);
        }
    }

    private List<Vote> votesFor(final UUID ballotId) {
        final var matched = new ArrayList<Vote>();
        final var votes = this.repository.list(Vote.class);
        for (final var vote : votes) {
            this.collectVote(matched, vote, ballotId);
        }
        return matched;
    }

    private void collectVote(final List<Vote> matched, final Vote vote, final UUID ballotId) {
        final var voteBallot = vote.getBallotId();
        if (ballotId.equals(voteBallot)) {
            matched.add(vote);
        }
    }
}
