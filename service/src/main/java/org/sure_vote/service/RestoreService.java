package org.sure_vote.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.sure_vote.entity.Election;
import org.sure_vote.entity.Race;
import org.sure_vote.entity.RestoreIncident;
import org.sure_vote.entity.VoteChain;
import org.sure_vote.entity.VoteChainValidationResult;
import org.sure_vote.repository.SureVoteRepository;

final class RestoreService {
    private final SureVoteRepository repository;
    private final Stores stores;
    private final ValidatorService validators;

    RestoreService(final SureVoteRepository repository, final Stores stores, final ValidatorService validators) {
        this.repository = repository;
        this.stores = stores;
        this.validators = validators;
    }

    String payload(final UUID electionId, final String reason, final Instant timestamp) {
        final var election = this.validators.publicElection(electionId);
        final var text = this.reason(reason);
        final var races = this.validators.races(electionId);
        final var witnessed = this.witnessedHeads(election, races);
        final var restored = this.restoredHeads(races);
        this.stores.require(timestamp != null, "timestamp is required");
        return Payloads.restoreIncident(electionId, witnessed, restored, text, timestamp);
    }

    RestoreIncident file(final UUID electionId, final RestoreRequest request, final UUID actor) {
        final var payload = this.payload(electionId, request.reason(), request.timestamp());
        final var election = this.stores.require(Election.class, electionId);
        final var races = this.validators.races(electionId);
        final var witnessed = this.witnessedHeads(election, races);
        final var hash = Digests.sha256Hex(payload);
        final var raw = Digests.unhex(hash);
        final var signed = this.signed(election, raw, request.signature());
        this.stores.require(actor != null, "actor is required");
        this.stores.require(this.diverged(witnessed), "the restored heads match the outside witnesses");
        this.stores.require(signed, "Operator signature does not match the restore incident");
        return this.insert(election, payload, hash, request.signature(), actor);
    }

    private boolean signed(final Election election, final byte[] raw, final String signature) {
        if (!Digests.isLowerHex(signature, 128)) {
            return false;
        }
        else {
            return Signatures.verify(election.getOperatorPublicKey(), raw, signature);
        }
    }

    List<RestoreIncident> incidents(final UUID electionId) {
        final var rows = this.repository.list(RestoreIncident.class);
        final var matched = new ArrayList<RestoreIncident>();
        this.validators.publicElection(electionId);
        for (final var row : rows) {
            this.keepIncident(matched, row, electionId);
        }
        return matched;
    }

    private void keepIncident(final List<RestoreIncident> matched, final RestoreIncident row, final UUID electionId) {
        final var same = electionId.equals(row.getElectionId());
        if (same) {
            matched.add(row);
        }
    }

    private String reason(final String reason) {
        final var text = this.trimmed(reason);
        this.stores.require(text != null, "restore reason is required");
        return text;
    }

    private String trimmed(final String value) {
        if (value == null) {
            return null;
        }
        else {
            final var text = value.trim();
            if (text.isEmpty()) {
                return null;
            }
            else {
                return text;
            }
        }
    }

    private List<ChainHead> restoredHeads(final List<Race> races) {
        final var heads = new ArrayList<ChainHead>();
        for (final var race : races) {
            final var chain = this.validators.chainFor(race.getId());
            heads.add(new ChainHead(race.getId(), chain.getLength(), chain.getHash()));
        }
        return heads;
    }

    private List<ChainHead> witnessedHeads(final Election election, final List<Race> races) {
        final var heads = new ArrayList<ChainHead>();
        for (final var race : races) {
            final var chain = this.validators.chainFor(race.getId());
            this.addWitness(heads, election.getTenantId(), chain);
        }
        return heads;
    }

    private void addWitness(final List<ChainHead> heads, final UUID tenantId, final VoteChain chain) {
        final var witness = this.latestWitness(tenantId, chain);
        if (witness != null) {
            heads.add(new ChainHead(chain.getRaceId(), witness.getLength(), witness.getValidatedThroughHash()));
        }
    }

    private VoteChainValidationResult latestWitness(final UUID tenantId, final VoteChain chain) {
        final var rows = this.repository.list(VoteChainValidationResult.class);
        final var matched = new ArrayList<VoteChainValidationResult>();
        for (final var row : rows) {
            this.keepWitness(matched, row, tenantId, chain);
        }
        return this.latest(matched);
    }

    private void keepWitness(
        final List<VoteChainValidationResult> matched,
        final VoteChainValidationResult row,
        final UUID tenantId,
        final VoteChain chain
    ) {
        if (this.witnesses(row, tenantId, chain)) {
            matched.add(row);
        }
    }

    private VoteChainValidationResult latest(final List<VoteChainValidationResult> matched) {
        if (matched.isEmpty()) {
            return null;
        }
        else {
            var best = matched.getFirst();
            for (final var row : matched) {
                best = this.later(row, best);
            }
            return best;
        }
    }

    private boolean witnesses(final VoteChainValidationResult row, final UUID tenantId, final VoteChain chain) {
        final var kept = row.getDeletedAt() == null;
        final var chainId = chain.getId();
        final var sameChain = chainId.equals(row.getVoteChainId());
        if (kept && sameChain) {
            return this.validators.registered(tenantId, row.getValidatorId());
        }
        else {
            return false;
        }
    }

    private VoteChainValidationResult later(
        final VoteChainValidationResult row,
        final VoteChainValidationResult best
    ) {
        final var validated = row.getDateValidated();
        final var longer = row.getLength() > best.getLength();
        final var sameLength = row.getLength() == best.getLength();
        if (longer || sameLength && validated.isAfter(best.getDateValidated())) {
            return row;
        }
        else {
            return best;
        }
    }

    private boolean diverged(final List<ChainHead> witnessed) {
        var found = false;
        for (final var head : witnessed) {
            found = found || this.offChain(head);
        }
        return found;
    }

    private boolean offChain(final ChainHead head) {
        final var chain = this.validators.chainFor(head.raceId());
        return !this.validators.onChain(chain, head.length(), head.headHash());
    }

    private RestoreIncident insert(
        final Election election,
        final String payload,
        final String hash,
        final String signature,
        final UUID actor
    ) {
        final var incident = new RestoreIncident();
        incident.setId(UUID.randomUUID());
        incident.setTenantId(election.getTenantId());
        incident.setCreatedAt(Instant.now());
        incident.setCreatedBy(actor);
        incident.setElectionId(election.getId());
        incident.setPayload(payload);
        incident.setHash(hash);
        incident.setSignature(signature);
        return this.stores.insert(incident);
    }
}
