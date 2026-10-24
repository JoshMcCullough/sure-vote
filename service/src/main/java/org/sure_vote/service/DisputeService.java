package org.sure_vote.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.BallotDispute;
import org.sure_vote.entity.DisputeStatus;
import org.sure_vote.entity.Election;
import org.sure_vote.repository.SureVoteRepository;

final class DisputeService {
    private final SureVoteRepository repository;
    private final Stores stores;
    private final LookupService lookup;

    DisputeService(
        final SureVoteRepository repository,
        final Stores stores,
        final LookupService lookup
    ) {
        this.repository = repository;
        this.stores = stores;
        this.lookup = lookup;
    }

    BallotDispute file(
        final UUID electionId,
        final String code,
        final String passphrase,
        final String statement
    ) {
        final var found = this.lookup.lookup(electionId, code, passphrase);
        final var ballot = found.ballot();
        final var text = this.statement(statement);
        return this.insert(ballot, text);
    }

    List<BallotDispute> forCode(final UUID electionId, final String code, final String passphrase) {
        final var found = this.lookup.lookup(electionId, code, passphrase);
        final var ballot = found.ballot();
        return this.forBallot(ballot.getId());
    }

    int count(final UUID electionId) {
        final var rows = this.counted(electionId);
        return rows.size();
    }

    List<BallotDispute> list(final UUID electionId, final UUID actor) {
        this.requireActor(actor);
        return this.counted(electionId);
    }

    BallotDispute setStatus(final UUID id, final DisputeStatus status, final UUID actor) {
        this.requireActor(actor);
        this.requireReview(status);
        return this.reviewed(id, status, actor);
    }

    private List<BallotDispute> counted(final UUID electionId) {
        this.stores.require(Election.class, electionId);
        return this.forElection(electionId);
    }

    private BallotDispute reviewed(final UUID id, final DisputeStatus status, final UUID actor) {
        final var existing = this.stores.require(BallotDispute.class, id);
        final var next = Copies.dispute(existing);
        next.setStatus(status);
        next.setUpdatedAt(Instant.now());
        next.setUpdatedBy(actor);
        next.setVersion(this.stores.nextVersion(existing.getVersion()));
        return this.stores.replace(next);
    }

    private BallotDispute insert(final Ballot ballot, final String statement) {
        final var now = Instant.now();
        final var row = new BallotDispute();
        row.setId(UUID.randomUUID());
        row.setTenantId(ballot.getTenantId());
        row.setBallotId(ballot.getId());
        row.setStatement(statement);
        row.setStatus(DisputeStatus.OPEN);
        row.setFiledAt(now);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        row.setVersion(Integer.valueOf(0));
        return this.stores.insert(row);
    }

    private List<BallotDispute> forElection(final UUID electionId) {
        final var matched = new ArrayList<BallotDispute>();
        final var disputes = this.repository.list(BallotDispute.class);
        for (final var dispute : disputes) {
            this.collectElection(matched, dispute, electionId);
        }
        return List.copyOf(matched);
    }

    private void collectElection(
        final List<BallotDispute> matched,
        final BallotDispute dispute,
        final UUID electionId
    ) {
        final var found = this.repository.find(Ballot.class, dispute.getBallotId());
        if (found.isPresent()) {
            this.collectBallot(matched, dispute, found.get(), electionId);
        }
    }

    private void collectBallot(
        final List<BallotDispute> matched,
        final BallotDispute dispute,
        final Ballot ballot,
        final UUID electionId
    ) {
        if (electionId.equals(ballot.getElectionId())) {
            matched.add(dispute);
        }
    }

    private List<BallotDispute> forBallot(final UUID ballotId) {
        final var matched = new ArrayList<BallotDispute>();
        final var disputes = this.repository.list(BallotDispute.class);
        for (final var dispute : disputes) {
            this.collectSameBallot(matched, dispute, ballotId);
        }
        return List.copyOf(matched);
    }

    private void collectSameBallot(
        final List<BallotDispute> matched,
        final BallotDispute dispute,
        final UUID ballotId
    ) {
        if (ballotId.equals(dispute.getBallotId())) {
            matched.add(dispute);
        }
    }

    private String statement(final String text) {
        if (text == null) {
            return null;
        }
        else {
            return this.trimmed(text);
        }
    }

    private String trimmed(final String text) {
        final var trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        else {
            return trimmed;
        }
    }

    private void requireActor(final UUID actor) {
        this.stores.require(actor != null, "actor is required");
    }

    private void requireReview(final DisputeStatus status) {
        final var dismissed = status == DisputeStatus.DISMISSED;
        final var noted = status == DisputeStatus.NOTED;
        this.stores.require(dismissed || noted, "dispute status must be dismissed or noted");
    }
}
