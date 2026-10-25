package org.sure_vote.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.CanvassStatement;
import org.sure_vote.entity.CloseBundle;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.ElectionStatus;
import org.sure_vote.entity.Race;
import org.sure_vote.entity.RestoreIncident;
import org.sure_vote.entity.Vote;
import org.sure_vote.entity.VoteChain;
import org.sure_vote.entity.VoteChainEntry;
import org.sure_vote.repository.SureVoteRepository;

/**
 * The bundle validators pull after close: every published ledger body for one election, signed by the operator.
 */
final class CloseBundleService {
    private final SureVoteRepository repository;
    private final Stores stores;
    private final ValidatorService validators;

    CloseBundleService(final SureVoteRepository repository, final Stores stores, final ValidatorService validators) {
        this.repository = repository;
        this.stores = stores;
        this.validators = validators;
    }

    String payload(final UUID electionId) {
        final var election = this.closedElection(electionId);
        final var races = this.sortedRaces(electionId);
        final var chains = this.chains(races);
        final var ballots = this.ballots(electionId);
        final var votes = this.votes(ballots);
        final var entries = this.entries(chains);
        final var canvass = this.canvass(electionId);
        final var incident = this.incident(electionId);
        return Payloads.closeBundle(election, chains, entries, ballots, votes, canvass, incident);
    }

    CloseBundle file(final UUID electionId, final String signature, final UUID actor) {
        final var payload = this.payload(electionId);
        final var election = this.stores.require(Election.class, electionId);
        final var hash = Digests.sha256Hex(payload);
        final var signed = Digests.isLowerHex(signature, 128)
            && Signatures.verify(election.getOperatorPublicKey(), Digests.unhex(hash), signature);
        final var existing = this.stored(electionId);
        this.stores.require(actor != null, "actor is required");
        this.stores.require(existing.isEmpty(), "An election has one close bundle");
        this.stores.require(signed, "Operator signature does not match the close bundle");
        return this.insert(election, hash, signature, actor);
    }

    Optional<CloseBundle> stored(final UUID electionId) {
        final var rows = this.repository.list(CloseBundle.class);
        final var stream = rows.stream();
        return stream
            .filter(row -> electionId.equals(row.getElectionId()))
            .findFirst();
    }

    BundleDownload download(final UUID electionId) {
        final var found = this.stored(electionId);
        final var bundle = found.orElseThrow(() -> new ConstraintException("Missing CloseBundle"));
        final var payload = this.payload(electionId);
        final var hash = Digests.sha256Hex(payload);
        if (!hash.equals(bundle.getHash())) {
            throw new IllegalStateException("The closed ledger no longer matches its signed bundle");
        }
        else {
            return new BundleDownload(payload, bundle.getHash(), bundle.getSignature());
        }
    }

    private Election closedElection(final UUID electionId) {
        final var election = this.stores.require(Election.class, electionId);
        this.stores.require(election.getStatus() == ElectionStatus.HISTORICAL, "Election is not closed");
        this.stores.requireUnfrozen(election);
        return election;
    }

    private List<Race> sortedRaces(final UUID electionId) {
        final var races = new ArrayList<>(this.validators.races(electionId));
        races.sort(Comparator.comparing(Race::getId));
        return races;
    }

    private List<VoteChain> chains(final List<Race> races) {
        final var chains = new ArrayList<VoteChain>();
        for (final var race : races) {
            chains.add(this.validators.chainFor(race.getId()));
        }
        return chains;
    }

    private List<Ballot> ballots(final UUID electionId) {
        final var rows = this.repository.list(Ballot.class);
        final var stream = rows.stream();
        return stream
            .filter(row -> electionId.equals(row.getElectionId()))
            .sorted(Comparator.comparing(Ballot::getHash))
            .toList();
    }

    private List<Vote> votes(final List<Ballot> ballots) {
        final var ids = new HashSet<UUID>();
        final var rows = this.repository.list(Vote.class);
        final var stream = rows.stream();
        ballots.forEach(ballot -> ids.add(ballot.getId()));
        return stream
            .filter(row -> ids.contains(row.getBallotId()))
            .sorted(Comparator.comparing(Vote::getHash))
            .toList();
    }

    private List<VoteChainEntry> entries(final List<VoteChain> chains) {
        final var rows = this.repository.list(VoteChainEntry.class);
        final var entries = new ArrayList<VoteChainEntry>();
        for (final var chain : chains) {
            entries.addAll(this.entriesOf(rows, chain));
        }
        return entries;
    }

    private List<VoteChainEntry> entriesOf(final List<VoteChainEntry> rows, final VoteChain chain) {
        final var stream = rows.stream();
        return stream
            .filter(row -> Objects.equals(chain.getId(), row.getVoteChainId()))
            .sorted(Comparator.comparingInt(VoteChainEntry::getIndex))
            .toList();
    }

    private CanvassStatement canvass(final UUID electionId) {
        final var rows = this.repository.list(CanvassStatement.class);
        final var stream = rows.stream();
        final var found = stream
            .filter(row -> electionId.equals(row.getElectionId()))
            .findFirst();
        return found.orElseThrow(() -> new ConstraintException("Missing CanvassStatement"));
    }

    private RestoreIncident incident(final UUID electionId) {
        final var rows = this.repository.list(RestoreIncident.class);
        final var stream = rows.stream();
        final var found = stream
            .filter(row -> electionId.equals(row.getElectionId()))
            .findFirst();
        return found.orElse(null);
    }

    private CloseBundle insert(final Election election, final String hash, final String signature, final UUID actor) {
        final var bundle = new CloseBundle();
        bundle.setId(UUID.randomUUID());
        bundle.setTenantId(election.getTenantId());
        bundle.setCreatedAt(Instant.now());
        bundle.setCreatedBy(actor);
        bundle.setElectionId(election.getId());
        bundle.setHash(hash);
        bundle.setSignature(signature);
        return this.repository.insert(bundle);
    }
}
