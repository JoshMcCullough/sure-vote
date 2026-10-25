package org.sure_vote.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.sure_vote.entity.CanvassStatement;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.ElectionStatus;
import org.sure_vote.entity.Race;
import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.TenantValidator;
import org.sure_vote.entity.VoteChain;
import org.sure_vote.entity.VoteChainEntry;
import org.sure_vote.entity.VoteChainValidationResult;
import org.sure_vote.entity.VoteChainValidator;
import org.sure_vote.repository.SureVoteRepository;

final class ValidatorService {
    private static final Duration WITNESS_WINDOW = Duration.ofHours(24);

    private final SureVoteRepository repository;
    private final Stores stores;

    ValidatorService(final SureVoteRepository repository, final Stores stores) {
        this.repository = repository;
        this.stores = stores;
    }

    VoteChainValidator register(final VoteChainValidator draft, final UUID actor) {
        final var name = this.requiredName(draft.getName());
        final var description = this.blankToNull(draft.getDescription());
        this.stores.require(actor != null, "actor is required");
        this.stores.require(name != null, "validator name is required");
        this.stores.require(Digests.isLowerHex(draft.getPublicKey(), 64), "validator public key");
        draft.setName(name);
        draft.setDescription(description);
        return this.stores.add(draft, actor);
    }

    TenantValidator link(final TenantValidator draft, final UUID actor) {
        final var tenant = this.stores.require(Tenant.class, draft.getTenantId());
        final var validator = this.stores.require(VoteChainValidator.class, draft.getValidatorId());
        final var tenantKept = tenant.getDeletedAt() == null;
        final var validatorKept = validator.getDeletedAt() == null;
        this.stores.require(actor != null, "actor is required");
        this.stores.require(tenantKept, "Missing Tenant");
        this.stores.require(validatorKept, "Missing VoteChainValidator");
        return this.stores.add(draft, actor);
    }

    VoteChainValidationResult file(final UUID electionId, final ValidationRequest request, final UUID actor) {
        final var chain = this.chain(request);
        final var race = this.stores.require(Race.class, chain.getRaceId());
        final var election = this.publicElection(electionId);
        final var validator = this.validator(request);
        final var raceElection = race.getElectionId();
        final var sameElection = electionId.equals(raceElection);
        this.stores.require(actor != null, "actor is required");
        this.stores.require(sameElection, "VoteChain is in another election");
        this.stores.sameTenant(chain.getTenantId(), election.getTenantId(), "VoteChain");
        this.requireLinked(election.getTenantId(), validator);
        this.requireShape(request);
        this.requireOnChain(chain, request);
        return this.store(chain, request, actor);
    }

    List<HeadNotice> heads(final UUID electionId) {
        final var election = this.publicElection(electionId);
        final var races = this.races(electionId);
        final var canvassHash = this.canvassHash(electionId);
        final var now = Instant.now();
        final var notices = new ArrayList<HeadNotice>();
        this.addHeads(notices, election, races, canvassHash, now);
        return notices;
    }

    List<VoteChainValidationResult> results(final UUID electionId) {
        final var election = this.publicElection(electionId);
        final var races = this.races(electionId);
        final var rows = this.repository.list(VoteChainValidationResult.class);
        final var matched = new ArrayList<VoteChainValidationResult>();
        this.collectResults(matched, rows, races, election.getTenantId());
        return matched;
    }

    List<VoteChainValidator> validators(final UUID electionId) {
        final var election = this.publicElection(electionId);
        final var links = this.repository.list(TenantValidator.class);
        final var matched = new ArrayList<VoteChainValidator>();
        this.collectValidators(matched, links, election.getTenantId());
        return matched;
    }

    private VoteChain chain(final ValidationRequest request) {
        this.stores.require(request.voteChainId() != null, "vote chain is required");
        return this.stores.require(VoteChain.class, request.voteChainId());
    }

    private VoteChainValidator validator(final ValidationRequest request) {
        final var id = request.validatorId();
        this.stores.require(id != null, "validator is required");
        return this.keptValidator(id);
    }

    private VoteChainValidator keptValidator(final UUID id) {
        final var validator = this.stores.require(VoteChainValidator.class, id);
        final var kept = validator.getDeletedAt() == null;
        this.stores.require(kept, "Missing VoteChainValidator");
        return validator;
    }

    private void requireLinked(final UUID tenantId, final VoteChainValidator validator) {
        final var links = this.repository.list(TenantValidator.class);
        final var linked = this.linked(links, tenantId, validator.getId());
        this.stores.require(linked, "validator is not registered for this tenant");
    }

    private boolean linked(final List<TenantValidator> links, final UUID tenantId, final UUID validatorId) {
        var found = false;
        for (final var link : links) {
            found = found || this.sameLink(link, tenantId, validatorId);
        }
        return found;
    }

    private boolean sameLink(final TenantValidator link, final UUID tenantId, final UUID validatorId) {
        final var kept = link.getDeletedAt() == null;
        final var sameTenant = tenantId.equals(link.getTenantId());
        final var sameValidator = validatorId.equals(link.getValidatorId());
        return kept && sameTenant && sameValidator;
    }

    private void requireShape(final ValidationRequest request) {
        final var method = this.method(request.method());
        final var hash = request.validatedThroughHash();
        final var hex = Digests.isLowerHex(hash, 64);
        this.stores.require(method != null, "validation method is required");
        this.stores.require(request.result() != null, "validation result is required");
        this.stores.require(request.dateValidated() != null, "dateValidated is required");
        this.stores.require(hex, "validatedThroughHash must be lowercase SHA-256 hex");
        this.evidence(request.evidenceHash());
    }

    private void requireOnChain(final VoteChain chain, final ValidationRequest request) {
        final var present = this.onChain(chain, request.length(), request.validatedThroughHash());
        this.stores.require(present, "validatedThroughHash is not on the chain");
    }

    private boolean onChain(final VoteChain chain, final int length, final String hash) {
        if (length == 0) {
            return this.emptyHead(chain, hash);
        }
        else if (length < 0 || length > chain.getLength()) {
            return false;
        }
        else {
            return this.entryMatches(chain.getId(), length - 1, hash);
        }
    }

    private boolean emptyHead(final VoteChain chain, final String hash) {
        final var payload = Payloads.emptyHead(chain.getRaceId());
        final var digest = Digests.sha256Hex(payload);
        return digest.equals(hash);
    }

    private boolean entryMatches(final UUID chainId, final int index, final String hash) {
        final var rows = this.repository.list(VoteChainEntry.class);
        var found = false;
        for (final var entry : rows) {
            found = found || this.sameEntry(entry, chainId, index, hash);
        }
        return found;
    }

    private boolean sameEntry(
        final VoteChainEntry entry,
        final UUID chainId,
        final int index,
        final String hash
    ) {
        final var sameChain = chainId.equals(entry.getVoteChainId());
        final var sameIndex = entry.getIndex() == index;
        final var sameHash = hash.equals(entry.getHash());
        return sameChain && sameIndex && sameHash;
    }

    private VoteChainValidationResult store(
        final VoteChain chain,
        final ValidationRequest request,
        final UUID actor
    ) {
        final var evidence = this.evidence(request.evidenceHash());
        final var method = this.method(request.method());
        final var payload = this.payload(request, evidence);
        final var hash = Digests.sha256Hex(payload);
        final var raw = Digests.unhex(hash);
        final var validator = this.stores.require(VoteChainValidator.class, request.validatorId());
        final var publicKey = validator.getPublicKey();
        final var signed = Signatures.verify(publicKey, raw, request.signature());
        this.stores.require(signed, "Validator signature does not match the validation result");
        return this.insert(chain, request, method, evidence, payload, hash, actor);
    }

    private String payload(final ValidationRequest request, final String evidence) {
        final var result = request.result();
        final var wire = result.wire();
        return Payloads.validation(
            request.voteChainId(),
            request.validatorId(),
            request.validatedThroughHash(),
            request.length(),
            wire,
            request.dateValidated(),
            request.notes(),
            evidence
        );
    }

    private VoteChainValidationResult insert(
        final VoteChain chain,
        final ValidationRequest request,
        final String method,
        final String evidence,
        final String payload,
        final String hash,
        final UUID actor
    ) {
        final var row = new VoteChainValidationResult();
        row.setTenantId(chain.getTenantId());
        row.setVoteChainId(chain.getId());
        row.setValidatorId(request.validatorId());
        row.setDateValidated(request.dateValidated());
        row.setValidatedThroughHash(request.validatedThroughHash());
        row.setLength(request.length());
        row.setMethod(method);
        row.setResult(request.result());
        row.setNotes(request.notes());
        row.setEvidenceHash(evidence);
        row.setPayload(payload);
        row.setHash(hash);
        row.setSignature(request.signature());
        return this.stores.add(row, actor);
    }

    private Election publicElection(final UUID electionId) {
        final var election = this.stores.require(Election.class, electionId);
        final var status = election.getStatus();
        final var open = status == ElectionStatus.ACTIVE || status == ElectionStatus.HISTORICAL;
        this.stores.require(open, "election is not public yet");
        return election;
    }

    private void addHeads(
        final List<HeadNotice> notices,
        final Election election,
        final List<Race> races,
        final String canvassHash,
        final Instant now
    ) {
        for (final var race : races) {
            final var chain = this.chainFor(race.getId());
            notices.add(this.notice(election, chain, canvassHash, now));
        }
    }

    private HeadNotice notice(
        final Election election,
        final VoteChain chain,
        final String canvassHash,
        final Instant now
    ) {
        final var gap = this.gap(election, chain, now);
        return new HeadNotice(
            election.getId(),
            chain.getRaceId(),
            chain.getLength(),
            chain.getHash(),
            chain.getUpdatedAt(),
            chain.getSignature(),
            canvassHash,
            gap
        );
    }

    private boolean gap(final Election election, final VoteChain chain, final Instant now) {
        final var active = election.getStatus() == ElectionStatus.ACTIVE;
        final var moved = chain.getLength() > 0;
        if (!active || !moved) {
            return false;
        }
        else if (this.witnessed(election.getTenantId(), chain)) {
            return false;
        }
        else {
            return this.late(chain.getUpdatedAt(), now);
        }
    }

    private boolean witnessed(final UUID tenantId, final VoteChain chain) {
        final var rows = this.repository.list(VoteChainValidationResult.class);
        var found = false;
        for (final var row : rows) {
            found = found || this.covers(row, tenantId, chain);
        }
        return found;
    }

    private boolean covers(
        final VoteChainValidationResult row,
        final UUID tenantId,
        final VoteChain chain
    ) {
        final var chainId = chain.getId();
        final var chainHash = chain.getHash();
        final var rowChain = row.getVoteChainId();
        final var rowHash = row.getValidatedThroughHash();
        final var kept = row.getDeletedAt() == null;
        final var sameChain = chainId.equals(rowChain);
        final var sameHash = chainHash.equals(rowHash);
        final var sameLength = chain.getLength() == row.getLength();
        if (kept && sameChain && sameHash && sameLength) {
            return this.registered(tenantId, row.getValidatorId());
        }
        else {
            return false;
        }
    }

    private boolean registered(final UUID tenantId, final UUID validatorId) {
        final var links = this.repository.list(TenantValidator.class);
        return this.linked(links, tenantId, validatorId);
    }

    private boolean late(final Instant observed, final Instant now) {
        if (observed == null) {
            return false;
        }
        else {
            final var deadline = observed.plus(WITNESS_WINDOW);
            return now.isAfter(deadline);
        }
    }

    private VoteChain chainFor(final UUID raceId) {
        final var rows = this.repository.list(VoteChain.class);
        final var matched = new ArrayList<VoteChain>();
        this.collectChains(matched, rows, raceId);
        this.stores.require(!matched.isEmpty(), "Missing VoteChain");
        return matched.getFirst();
    }

    private void collectChains(final List<VoteChain> matched, final List<VoteChain> rows, final UUID raceId) {
        for (final var row : rows) {
            this.keepChain(matched, row, raceId);
        }
    }

    private void keepChain(final List<VoteChain> matched, final VoteChain row, final UUID raceId) {
        final var kept = row.getDeletedAt() == null;
        final var same = raceId.equals(row.getRaceId());
        if (kept && same) {
            matched.add(row);
        }
    }

    private String canvassHash(final UUID electionId) {
        final var rows = this.repository.list(CanvassStatement.class);
        final var matched = new ArrayList<CanvassStatement>();
        this.collectCanvass(matched, rows, electionId);
        if (matched.isEmpty()) {
            return null;
        }
        else {
            final var statement = matched.getFirst();
            return statement.getHash();
        }
    }

    private void collectCanvass(
        final List<CanvassStatement> matched,
        final List<CanvassStatement> rows,
        final UUID electionId
    ) {
        for (final var row : rows) {
            this.keepCanvass(matched, row, electionId);
        }
    }

    private void keepCanvass(
        final List<CanvassStatement> matched,
        final CanvassStatement row,
        final UUID electionId
    ) {
        final var same = electionId.equals(row.getElectionId());
        if (same) {
            matched.add(row);
        }
    }

    private List<Race> races(final UUID electionId) {
        final var rows = this.repository.list(Race.class);
        final var matched = new ArrayList<Race>();
        for (final var race : rows) {
            this.insertRace(matched, race, electionId);
        }
        return matched;
    }

    private void insertRace(final List<Race> matched, final Race race, final UUID electionId) {
        final var kept = race.getDeletedAt() == null;
        final var raceElection = race.getElectionId();
        final var same = electionId.equals(raceElection);
        if (kept && same) {
            this.placeRace(matched, race);
        }
    }

    private void placeRace(final List<Race> matched, final Race race) {
        var index = 0;
        while (index < matched.size() && this.before(matched.get(index), race)) {
            index = index + 1;
        }
        matched.add(index, race);
    }

    private boolean before(final Race left, final Race right) {
        final var leftKey = left.getId();
        final var rightKey = right.getId();
        final var leftId = leftKey.toString();
        final var rightId = rightKey.toString();
        return leftId.compareTo(rightId) < 0;
    }

    private void collectResults(
        final List<VoteChainValidationResult> matched,
        final List<VoteChainValidationResult> rows,
        final List<Race> races,
        final UUID tenantId
    ) {
        for (final var row : rows) {
            this.keepResult(matched, row, races, tenantId);
        }
    }

    private void keepResult(
        final List<VoteChainValidationResult> matched,
        final VoteChainValidationResult row,
        final List<Race> races,
        final UUID tenantId
    ) {
        final var kept = row.getDeletedAt() == null;
        final var sameTenant = tenantId.equals(row.getTenantId());
        if (kept && sameTenant && this.onRaces(row, races)) {
            matched.add(row);
        }
    }

    private boolean onRaces(final VoteChainValidationResult row, final List<Race> races) {
        var found = false;
        for (final var race : races) {
            found = found || this.chainOf(race, row.getVoteChainId());
        }
        return found;
    }

    private boolean chainOf(final Race race, final UUID chainId) {
        final var chain = this.chainFor(race.getId());
        return chainId.equals(chain.getId());
    }

    private void collectValidators(
        final List<VoteChainValidator> matched,
        final List<TenantValidator> links,
        final UUID tenantId
    ) {
        for (final var link : links) {
            this.keepValidator(matched, link, tenantId);
        }
    }

    private void keepValidator(
        final List<VoteChainValidator> matched,
        final TenantValidator link,
        final UUID tenantId
    ) {
        final var kept = link.getDeletedAt() == null;
        final var same = tenantId.equals(link.getTenantId());
        if (kept && same) {
            this.addValidator(matched, link.getValidatorId());
        }
    }

    private void addValidator(final List<VoteChainValidator> matched, final UUID validatorId) {
        final var found = this.repository.find(VoteChainValidator.class, validatorId);
        if (found.isPresent()) {
            final var validator = found.get();
            this.keepLive(matched, validator);
        }
    }

    private void keepLive(final List<VoteChainValidator> matched, final VoteChainValidator validator) {
        final var kept = validator.getDeletedAt() == null;
        if (kept) {
            matched.add(validator);
        }
    }

    private String requiredName(final String name) {
        return this.method(name);
    }

    private String method(final String value) {
        if (value == null) {
            return null;
        }
        else {
            final var trimmed = value.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            else {
                return trimmed;
            }
        }
    }

    private String blankToNull(final String value) {
        return this.method(value);
    }

    private String evidence(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        else {
            final var hex = Digests.isLowerHex(value, 64);
            this.stores.require(hex, "evidenceHash must be lowercase SHA-256 hex");
            return value;
        }
    }
}
