package org.sure_vote.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.Vote;
import org.sure_vote.repository.SureVoteRepository;

final class LookupService {
    private final SureVoteRepository repository;
    private final Stores stores;

    LookupService(final SureVoteRepository repository, final Stores stores) {
        this.repository = repository;
        this.stores = stores;
    }

    LookupResult lookup(final UUID electionId, final String code, final String passphrase) {
        final var raw = this.decode(code);
        final var hash = AccessCodes.inPersonHash(raw);
        final var ballot = this.findOrNull(electionId, hash);
        if (ballot != null) {
            return this.result(ballot);
        }
        else {
            return this.byPassphrase(electionId, raw, passphrase);
        }
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

    private LookupResult result(final Ballot ballot) {
        final var votes = this.repository.inTenant(ballot.getTenantId(), () -> this.unfrozenVotes(ballot));
        return new LookupResult(ballot, votes);
    }

    private List<Vote> unfrozenVotes(final Ballot ballot) {
        final var election = this.stores.require(Election.class, ballot.getElectionId());
        this.stores.requireUnfrozen(election);
        return this.votesFor(ballot.getId());
    }

    private LookupResult byPassphrase(final UUID electionId, final byte[] raw, final String passphrase) {
        final var normalized = Passphrases.normalize(passphrase);
        if (normalized.isEmpty()) {
            throw new ConstraintException("no ballot for that code");
        }
        else {
            return this.byMailHash(electionId, raw, normalized);
        }
    }

    private LookupResult byMailHash(final UUID electionId, final byte[] raw, final String normalized) {
        final var hash = AccessCodes.mailHash(normalized, raw);
        final var ballot = this.find(electionId, hash);
        return this.result(ballot);
    }

    private Ballot find(final UUID electionId, final String hash) {
        final var ballot = this.findOrNull(electionId, hash);
        if (ballot == null) {
            throw new ConstraintException("no ballot for that code");
        }
        else {
            return ballot;
        }
    }

    private Ballot findOrNull(final UUID electionId, final String hash) {
        final var ballots = this.repository.ballotsByAccessHash(hash);
        final var stream = ballots.stream();
        final var matched = stream.filter(ballot -> electionId == null || electionId.equals(ballot.getElectionId()));
        final var first = matched.findFirst();
        return first.orElse(null);
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
