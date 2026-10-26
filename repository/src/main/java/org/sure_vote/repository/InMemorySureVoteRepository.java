package org.sure_vote.repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.Candidate;
import org.sure_vote.entity.ElectionStatus;
import org.sure_vote.entity.Person;
import org.sure_vote.entity.Race;
import org.sure_vote.entity.RaceCandidate;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.HeldKey;
import org.sure_vote.entity.HeldKeyPurpose;
import org.sure_vote.entity.BallotIssuance;
import org.sure_vote.entity.BallotStyle;
import org.sure_vote.entity.CanvassStatement;
import org.sure_vote.entity.CloseBundle;
import org.sure_vote.entity.Identifiable;
import org.sure_vote.entity.IssuanceStatus;
import org.sure_vote.entity.LedgerEntity;
import org.sure_vote.entity.RequestNonce;
import org.sure_vote.entity.RestoreIncident;
import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.TenantEntity;
import org.sure_vote.entity.TenantValidator;
import org.sure_vote.entity.Vote;
import org.sure_vote.entity.VoteChain;
import org.sure_vote.entity.VoteChainEntry;
import org.sure_vote.entity.VoteChainValidationResult;

/**
 * Map-backed store. A transaction snapshots map references and restores them on failure.
 * Replace a stored row with a new instance. Mutating an instance already in a map is not rolled back.
 * Inside {@code inTenant} or {@code asSystemAdmin} it applies the tenant row-level security policies the way a
 * non-owner role sees them. Outside a scope it behaves as the table owner.
 */
public final class InMemorySureVoteRepository implements SureVoteRepository {
    private static final Set<Class<?>> INSERT_ONLY = Set.of(
        Ballot.class,
        Vote.class,
        VoteChainEntry.class,
        CanvassStatement.class,
        HeldKey.class,
        RestoreIncident.class,
        CloseBundle.class,
        VoteChainValidationResult.class
    );

    private final Object lock = new Object();
    private final TenantLock tenantLock = new TenantLock();
    private final ThreadLocal<List<Runnable>> after = new ThreadLocal<>();
    private final ThreadLocal<Scope> scope = new ThreadLocal<>();
    private Map<Class<?>, Map<UUID, Identifiable>> rows = new LinkedHashMap<>();

    @Override
    public <T extends Identifiable> T insert(final T row) {
        synchronized (this.lock) {
            this.requireWritable(row);
            return this.insertLocked(row);
        }
    }

    @Override
    public <T extends Identifiable> T replace(final T row) {
        synchronized (this.lock) {
            this.requireWritable(row);
            this.requireWritable(this.storedRow(row));
            return this.replaceLocked(row);
        }
    }

    @Override
    public <T extends Identifiable> Optional<T> find(final Class<T> type, final UUID id) {
        synchronized (this.lock) {
            final var found = this.findLocked(type, id);
            return found.filter(this::visible);
        }
    }

    @Override
    public <T extends Identifiable> List<T> list(final Class<T> type) {
        synchronized (this.lock) {
            final var rows = this.listLocked(type);
            final var stream = rows.stream();
            return stream
                .filter(this::visible)
                .toList();
        }
    }

    @Override
    public <T> T transaction(final Supplier<T> work) {
        final var joined = this.after.get();
        if (joined == null) {
            return this.outer(work);
        }
        else {
            return this.nested(joined, work);
        }
    }

    @Override
    public int pruneNonces(final Instant before) {
        synchronized (this.lock) {
            return this.pruneLocked(before);
        }
    }

    private int pruneLocked(final Instant before) {
        final var currentRows = this.rows;
        final var nextTable = new LinkedHashMap<UUID, Identifiable>();
        final var nextRows = new LinkedHashMap<Class<?>, Map<UUID, Identifiable>>();
        final var nonces = this.listLocked(RequestNonce.class);
        final var kept = nonces
            .stream()
            .filter(nonce -> fresh(nonce, before))
            .toList();
        final var removed = nonces.size() - kept.size();
        kept.forEach(nonce -> nextTable.put(nonce.getId(), nonce));
        nextRows.putAll(currentRows);
        nextRows.put(RequestNonce.class, nextTable);
        this.rows = nextRows;
        return removed;
    }

    private static boolean fresh(final RequestNonce nonce, final Instant before) {
        final var at = nonce.getRequestTimestamp();
        return !at.isBefore(before);
    }

    @Override
    public void lock(final UUID key) {
        this.requirePresent(key, "a lock key");
    }

    @Override
    public void afterCommit(final Runnable action) {
        final var joined = this.after.get();
        if (joined == null) {
            action.run();
        }
        else {
            joined.add(action);
        }
    }

    private <T> T outer(final Supplier<T> work) {
        final var pending = new ArrayList<Runnable>();
        final var result = this.committed(pending, work);
        pending.forEach(Runnable::run);
        return result;
    }

    private <T> T committed(final List<Runnable> pending, final Supplier<T> work) {
        this.after.set(pending);
        try {
            return this.nested(pending, work);
        }
        finally {
            this.after.remove();
        }
    }

    private <T> T nested(final List<Runnable> pending, final Supplier<T> work) {
        synchronized (this.lock) {
            final var snapshot = this.rows;
            final var size = pending.size();
            try {
                return work.get();
            }
            catch (final RuntimeException failure) {
                final var added = pending.subList(size, pending.size());
                this.rows = snapshot;
                added.clear();
                throw failure;
            }
        }
    }

    @Override
    public List<Ballot> ballotsByAccessHash(final String voterAccessHash) {
        final var ballots = this.owned(Ballot.class);
        final var stream = ballots.stream();
        final var matched = stream.filter(ballot -> voterAccessHash.equals(ballot.getVoterAccessHash()));
        return matched.toList();
    }

    @Override
    public int markReceived(final List<UUID> issuanceIds, final Instant receivedAt) {
        final var day = receivedAt.truncatedTo(ChronoUnit.DAYS);
        if (!day.equals(receivedAt)) {
            throw new IllegalArgumentException("received_at must be a whole UTC day");
        }
        else {
            synchronized (this.lock) {
                return this.markLocked(issuanceIds, receivedAt);
            }
        }
    }

    private int markLocked(final List<UUID> issuanceIds, final Instant receivedAt) {
        final var counts = issuanceIds
            .stream()
            .mapToInt(id -> this.markOne(id, receivedAt));
        return counts.sum();
    }

    private int markOne(final UUID id, final Instant receivedAt) {
        final var found = this.findLocked(BallotIssuance.class, id);
        final var issued = found.filter(row -> row.getStatus() == IssuanceStatus.ISSUED);
        if (issued.isEmpty()) {
            return 0;
        }
        else {
            this.store(BallotIssuance.class, received(issued.get(), receivedAt));
            return 1;
        }
    }

    private static BallotIssuance received(final BallotIssuance source, final Instant receivedAt) {
        final var copy = new BallotIssuance();
        copy.setId(source.getId());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        copy.setCreatedBy(source.getCreatedBy());
        copy.setUpdatedBy(source.getUpdatedBy());
        copy.setDeletedAt(source.getDeletedAt());
        copy.setDeletedBy(source.getDeletedBy());
        copy.setVersion(source.getVersion());
        copy.setTenantId(source.getTenantId());
        copy.setPersonId(source.getPersonId());
        copy.setElectionId(source.getElectionId());
        copy.setRecordingPlaceId(source.getRecordingPlaceId());
        copy.setAcceptedAt(source.getAcceptedAt());
        copy.setVoidReason(source.getVoidReason());
        copy.setStatus(IssuanceStatus.RECEIVED);
        copy.setReceivedAt(receivedAt);
        return copy;
    }

    @Override
    public Optional<UUID> electionTenant(final UUID electionId) {
        final var election = this.ownedRow(Election.class, electionId);
        final var live = election.filter(row -> row.getDeletedAt() == null);
        return live.map(Election::getTenantId);
    }

    @Override
    public Map<UUID, String> candidateNames(final UUID electionId) {
        final var names = new LinkedHashMap<UUID, String>();
        final var election = this.ownedRow(Election.class, electionId);
        final var published = election.filter(this::published);
        published.ifPresent(row -> this.collectNames(names, electionId));
        return names;
    }

    private boolean published(final Election election) {
        final var status = election.getStatus();
        return election.getDeletedAt() == null && status != ElectionStatus.UPCOMING;
    }

    private void collectNames(final Map<UUID, String> names, final UUID electionId) {
        for (final var slot : this.owned(RaceCandidate.class)) {
            final var race = this.ownedRow(Race.class, slot.getRaceId());
            final var inElection = race.filter(row -> this.liveIn(row, electionId));
            if (slot.getDeletedAt() == null && inElection.isPresent()) {
                this.addName(names, slot);
            }
        }
    }

    private boolean liveIn(final Race race, final UUID electionId) {
        return electionId.equals(race.getElectionId()) && race.getDeletedAt() == null;
    }

    private void addName(final Map<UUID, String> names, final RaceCandidate slot) {
        final var candidate = this.ownedRow(Candidate.class, slot.getCandidateId());
        final var live = candidate.filter(row -> row.getDeletedAt() == null);
        final var person = live.flatMap(row -> this.ownedRow(Person.class, row.getPersonId()));
        final var livePerson = person.filter(row -> row.getDeletedAt() == null);
        final var name = livePerson.map(this::displayName);
        name.ifPresent(value -> names.put(slot.getId(), value));
    }

    private String displayName(final Person person) {
        final var full = person.getFullName();
        if (full != null && !full.isBlank()) {
            return full.strip();
        }
        else {
            final var parts = new ArrayList<String>();
            this.addPart(parts, person.getFirstName());
            this.addPart(parts, person.getMiddleName());
            this.addPart(parts, person.getLastName());
            return String.join(" ", parts);
        }
    }

    private void addPart(final List<String> parts, final String part) {
        if (part != null) {
            parts.add(part);
        }
    }

    @Override
    public <T> T inTenant(final UUID tenantId, final Supplier<T> work) {
        final var current = this.currentScope();
        this.tenantLock.check(tenantId);
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        else if (current.tenantId() != null && !Objects.equals(current.tenantId(), tenantId)) {
            throw new IllegalArgumentException("A transaction serves one tenant");
        }
        else {
            return this.transaction(() -> this.within(new Scope(tenantId, current.adminId()), work));
        }
    }

    @Override
    public void lockTenants(final Set<UUID> tenantIds) {
        this.tenantLock.lock(tenantIds);
    }

    @Override
    public <T> T asSystemAdmin(final UUID adminId, final Supplier<T> work) {
        final var current = this.currentScope();
        if (adminId == null) {
            throw new IllegalArgumentException("adminId is required");
        }
        else if (current.adminId() != null && !Objects.equals(current.adminId(), adminId)) {
            throw new IllegalArgumentException("A transaction serves one system admin");
        }
        else {
            return this.transaction(() -> this.within(new Scope(current.tenantId(), adminId), work));
        }
    }

    private <T> T within(final Scope next, final Supplier<T> work) {
        final var previous = this.scope.get();
        this.scope.set(next);
        try {
            return work.get();
        }
        finally {
            this.restore(previous);
        }
    }

    private void restore(final Scope previous) {
        if (previous == null) {
            this.scope.remove();
        }
        else {
            this.scope.set(previous);
        }
    }

    private Scope currentScope() {
        final var current = this.scope.get();
        return Objects.requireNonNullElse(current, Scope.OWNER);
    }

    private boolean visible(final Identifiable row) {
        final var current = this.currentScope();
        return current.owner() || current.sees(row);
    }

    private void requireWritable(final Identifiable row) {
        if (row != null && !this.visible(row)) {
            final var type = row.getClass();
            throw new IllegalArgumentException("Row-level security refuses this " + type.getSimpleName());
        }
    }

    private Identifiable storedRow(final Identifiable row) {
        if (row == null || row.getId() == null) {
            return null;
        }
        else {
            final var current = this.table(row.getClass());
            return current.get(row.getId());
        }
    }

    private <T extends Identifiable> List<T> owned(final Class<T> type) {
        synchronized (this.lock) {
            return this.listLocked(type);
        }
    }

    private <T extends Identifiable> Optional<T> ownedRow(final Class<T> type, final UUID id) {
        synchronized (this.lock) {
            return this.findLocked(type, id);
        }
    }

    private record Scope(UUID tenantId, UUID adminId) {
        static final Scope OWNER = new Scope(null, null);

        boolean owner() {
            return this.tenantId == null && this.adminId == null;
        }

        boolean sees(final Identifiable row) {
            if (row instanceof Tenant tenant) {
                return this.adminId != null || Objects.equals(this.tenantId, tenant.getId());
            }
            else if (row instanceof TenantEntity owned) {
                return this.tenantId != null && this.tenantId.equals(owned.getTenantId());
            }
            else if (row instanceof LedgerEntity owned) {
                return this.tenantId != null && this.tenantId.equals(owned.getTenantId());
            }
            else {
                return true;
            }
        }
    }

    private <T extends Identifiable> T insertLocked(final T row) {
        this.requireRow(row);
        return this.insertChecked(row);
    }

    private <T extends Identifiable> T insertChecked(final T row) {
        final var type = row.getClass();
        final var current = this.table(type);
        if (current.containsKey(row.getId())) {
            throw new IllegalArgumentException("Duplicate id for " + type.getSimpleName());
        }
        else {
            this.requireUnique(row, null);
            this.store(type, row);
            return row;
        }
    }

    private <T extends Identifiable> T replaceLocked(final T row) {
        this.requireRow(row);
        return this.replaceChecked(row);
    }

    private <T extends Identifiable> T replaceChecked(final T row) {
        final var type = row.getClass();
        if (INSERT_ONLY.contains(type)) {
            throw new IllegalArgumentException(type.getSimpleName() + " is insert-only");
        }
        else {
            return this.replaceExisting(row, type);
        }
    }

    private <T extends Identifiable> T replaceExisting(final T row, final Class<?> type) {
        final var current = this.table(type);
        if (!current.containsKey(row.getId())) {
            throw new IllegalArgumentException("Unknown id for " + type.getSimpleName());
        }
        else {
            this.requireUnique(row, row.getId());
            this.store(type, row);
            return row;
        }
    }

    private <T extends Identifiable> Optional<T> findLocked(final Class<T> type, final UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        else {
            final var current = this.table(type);
            final var row = current.get(id);
            if (row == null) {
                return Optional.empty();
            }
            else {
                return Optional.of(type.cast(row));
            }
        }
    }

    private <T extends Identifiable> List<T> listLocked(final Class<T> type) {
        final var current = this.table(type);
        final var copy = new ArrayList<T>();
        final var values = current.values();
        for (final var row : values) {
            copy.add(type.cast(row));
        }
        return List.copyOf(copy);
    }

    private void store(final Class<?> type, final Identifiable row) {
        final var currentRows = this.rows;
        final var currentTable = currentRows.get(type);
        final var nextTable = new LinkedHashMap<UUID, Identifiable>();
        final var nextRows = new LinkedHashMap<Class<?>, Map<UUID, Identifiable>>();
        this.fill(nextTable, currentTable);
        nextTable.put(row.getId(), row);
        nextRows.putAll(currentRows);
        nextRows.put(type, nextTable);
        this.rows = nextRows;
    }

    private void fill(
        final Map<UUID, Identifiable> nextTable,
        final Map<UUID, Identifiable> currentTable
    ) {
        if (currentTable != null) {
            nextTable.putAll(currentTable);
        }
    }

    private Map<UUID, Identifiable> table(final Class<?> type) {
        final var current = this.rows.get(type);
        if (current == null) {
            return Map.of();
        }
        else {
            return current;
        }
    }

    private void requireRow(final Identifiable row) {
        if (row == null) {
            throw new IllegalArgumentException("row is required");
        }
        else if (row.getId() == null) {
            throw new IllegalArgumentException("id is required");
        }
    }

    private void requireUnique(final Identifiable row, final UUID ignoredId) {
        if (row instanceof Ballot ballot) {
            this.requireBallot(ballot, ignoredId);
        }
        else if (row instanceof VoteChain chain) {
            this.requireChain(chain, ignoredId);
        }
        else if (row instanceof VoteChainEntry entry) {
            this.requireEntry(entry, ignoredId);
        }
        else if (row instanceof BallotIssuance issuance) {
            this.requireIssuance(issuance, ignoredId);
        }
        else if (row instanceof CanvassStatement statement) {
            this.requireCanvass(statement, ignoredId);
        }
        else if (row instanceof RestoreIncident incident) {
            this.requireIncident(incident, ignoredId);
        }
        else if (row instanceof CloseBundle bundle) {
            this.requireBundle(bundle, ignoredId);
        }
        else if (row instanceof BallotStyle style) {
            this.requireStyle(style, ignoredId);
        }
        else if (row instanceof TenantValidator link) {
            this.requireValidator(link, ignoredId);
        }
        else if (row instanceof HeldKey key) {
            this.requireHeld(key, ignoredId);
        }
    }

    private void requireBallot(final Ballot ballot, final UUID ignoredId) {
        this.requireText(ballot.getVoterAccessHash(), "voterAccessHash");
        this.requirePresent(ballot.getElectionId(), "electionId");
        this.scanBallots(ballot, ignoredId);
    }

    private void scanBallots(final Ballot ballot, final UUID ignoredId) {
        final var rows = this.listLocked(Ballot.class);
        for (final var existing : rows) {
            final var accessHash = existing.getVoterAccessHash();
            final var sameHash = accessHash.equals(ballot.getVoterAccessHash());
            final var sameId = this.sameId(existing.getId(), ignoredId);
            if (!sameId && sameHash) {
                throw new IllegalArgumentException("Duplicate voter access hash");
            }
        }
    }

    private void requireChain(final VoteChain chain, final UUID ignoredId) {
        this.requirePresent(chain.getRaceId(), "raceId");
        this.scanChains(chain, ignoredId);
    }

    private void scanChains(final VoteChain chain, final UUID ignoredId) {
        final var rows = this.listLocked(VoteChain.class);
        for (final var existing : rows) {
            final var raceId = existing.getRaceId();
            final var sameRace = raceId.equals(chain.getRaceId());
            final var sameId = this.sameId(existing.getId(), ignoredId);
            if (!sameId && sameRace) {
                throw new IllegalArgumentException("A race has one vote chain");
            }
        }
    }

    private void requireEntry(final VoteChainEntry entry, final UUID ignoredId) {
        this.requirePresent(entry.getVoteId(), "voteId");
        this.requirePresent(entry.getVoteChainId(), "voteChainId");
        this.requireNonce(entry.getNonce());
        this.scanEntries(entry, ignoredId);
    }

    private void scanEntries(final VoteChainEntry entry, final UUID ignoredId) {
        final var rows = this.listLocked(VoteChainEntry.class);
        for (final var existing : rows) {
            final var sameId = this.sameId(existing.getId(), ignoredId);
            final var voteId = existing.getVoteId();
            final var sameVote = voteId.equals(entry.getVoteId());
            final var chainId = existing.getVoteChainId();
            final var sameChain = chainId.equals(entry.getVoteChainId());
            final var sameIndex = existing.getIndex() == entry.getIndex();
            final var nonce = existing.getNonce();
            final var sameNonce = nonce.equals(entry.getNonce());
            if (!sameId && sameVote) {
                throw new IllegalArgumentException("Duplicate vote on a chain");
            }
            else if (!sameId && sameChain && sameIndex) {
                throw new IllegalArgumentException("Duplicate chain index");
            }
            else if (!sameId && sameChain && sameNonce) {
                throw new IllegalArgumentException("Duplicate chain nonce");
            }
        }
    }

    private void requireIssuance(final BallotIssuance issuance, final UUID ignoredId) {
        final var status = issuance.getStatus();
        if (status == null || !status.spends()) {
            return;
        }
        else {
            this.scanAcceptedIssuances(issuance, ignoredId);
        }
    }

    private void scanAcceptedIssuances(final BallotIssuance issuance, final UUID ignoredId) {
        this.requirePresent(issuance.getPersonId(), "personId");
        this.requirePresent(issuance.getElectionId(), "electionId");
        this.scanIssuances(issuance, ignoredId);
    }

    private void scanIssuances(final BallotIssuance issuance, final UUID ignoredId) {
        final var rows = this.listLocked(BallotIssuance.class);
        for (final var existing : rows) {
            final var personId = existing.getPersonId();
            final var samePerson = personId.equals(issuance.getPersonId());
            final var electionId = existing.getElectionId();
            final var sameElection = electionId.equals(issuance.getElectionId());
            final var status = existing.getStatus();
            final var accepted = status != null && status.spends();
            final var sameId = this.sameId(existing.getId(), ignoredId);
            if (!sameId && samePerson && sameElection && accepted) {
                throw new IllegalArgumentException("Voter already has an issued ballot");
            }
        }
    }

    private void requireCanvass(final CanvassStatement statement, final UUID ignoredId) {
        this.requirePresent(statement.getElectionId(), "electionId");
        this.scanCanvasses(statement, ignoredId);
    }

    private void scanCanvasses(final CanvassStatement statement, final UUID ignoredId) {
        final var rows = this.listLocked(CanvassStatement.class);
        for (final var existing : rows) {
            final var electionId = existing.getElectionId();
            final var sameElection = electionId.equals(statement.getElectionId());
            final var sameId = this.sameId(existing.getId(), ignoredId);
            if (!sameId && sameElection) {
                throw new IllegalArgumentException("An election has one canvass statement");
            }
        }
    }

    private void requireIncident(final RestoreIncident incident, final UUID ignoredId) {
        this.requirePresent(incident.getElectionId(), "electionId");
        this.scanIncidents(incident, ignoredId);
    }

    private void scanIncidents(final RestoreIncident incident, final UUID ignoredId) {
        final var rows = this.listLocked(RestoreIncident.class);
        for (final var existing : rows) {
            final var electionId = existing.getElectionId();
            final var sameElection = electionId.equals(incident.getElectionId());
            final var sameId = this.sameId(existing.getId(), ignoredId);
            if (!sameId && sameElection) {
                throw new IllegalArgumentException("An election has one restore incident");
            }
        }
    }

    private void requireBundle(final CloseBundle bundle, final UUID ignoredId) {
        this.requirePresent(bundle.getElectionId(), "electionId");
        for (final var existing : this.listLocked(CloseBundle.class)) {
            final var electionId = existing.getElectionId();
            final var sameElection = electionId.equals(bundle.getElectionId());
            final var sameId = this.sameId(existing.getId(), ignoredId);
            if (!sameId && sameElection) {
                throw new IllegalArgumentException("An election has one close bundle");
            }
        }
    }

    private void requireStyle(final BallotStyle style, final UUID ignoredId) {
        this.requirePresent(style.getElectionId(), "electionId");
        this.requirePresent(style.getVotingPrecinctId(), "votingPrecinctId");
        this.requirePresent(style.getRaceId(), "raceId");
        this.scanStyles(style, ignoredId);
    }

    private void scanStyles(final BallotStyle style, final UUID ignoredId) {
        final var rows = this.listLocked(BallotStyle.class);
        for (final var existing : rows) {
            final var electionId = existing.getElectionId();
            final var sameElection = electionId.equals(style.getElectionId());
            final var precinctId = existing.getVotingPrecinctId();
            final var samePrecinct = precinctId.equals(style.getVotingPrecinctId());
            final var raceId = existing.getRaceId();
            final var sameRace = raceId.equals(style.getRaceId());
            final var sameId = this.sameId(existing.getId(), ignoredId);
            if (!sameId && sameElection && samePrecinct && sameRace) {
                throw new IllegalArgumentException("Duplicate ballot style row");
            }
        }
    }

    private void requireValidator(final TenantValidator link, final UUID ignoredId) {
        this.requirePresent(link.getTenantId(), "tenantId");
        this.requirePresent(link.getValidatorId(), "validatorId");
        this.scanValidators(link, ignoredId);
    }

    private void scanValidators(final TenantValidator link, final UUID ignoredId) {
        final var rows = this.listLocked(TenantValidator.class);
        for (final var existing : rows) {
            final var tenantId = existing.getTenantId();
            final var sameTenant = tenantId.equals(link.getTenantId());
            final var validatorId = existing.getValidatorId();
            final var sameValidator = validatorId.equals(link.getValidatorId());
            final var sameId = this.sameId(existing.getId(), ignoredId);
            if (!sameId && sameTenant && sameValidator) {
                throw new IllegalArgumentException("Duplicate tenant validator");
            }
        }
    }

    private void requireHeld(final HeldKey key, final UUID ignoredId) {
        this.requirePresent(key.getSubjectId(), "subjectId");
        this.requireText(key.getSeed(), "seed");
        this.requirePurpose(key.getPurpose());
        this.scanHeld(key, ignoredId);
    }

    private void requirePurpose(final HeldKeyPurpose purpose) {
        if (purpose == null) {
            throw new IllegalArgumentException("purpose is required");
        }
    }

    private void scanHeld(final HeldKey key, final UUID ignoredId) {
        final var rows = this.listLocked(HeldKey.class);
        for (final var existing : rows) {
            final var purpose = existing.getPurpose();
            final var samePurpose = purpose == key.getPurpose();
            final var subjectId = existing.getSubjectId();
            final var sameSubject = subjectId.equals(key.getSubjectId());
            final var sameId = this.sameId(existing.getId(), ignoredId);
            if (!sameId && samePurpose && sameSubject) {
                throw new IllegalArgumentException("Duplicate showcase key");
            }
        }
    }

    private void requireNonce(final String nonce) {
        if (nonce == null || nonce.length() < 1 || nonce.length() > 64) {
            throw new IllegalArgumentException("nonce must be 1 to 64 characters");
        }
    }

    private void requireText(final String value, final String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
    }

    private void requirePresent(final UUID value, final String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
    }

    private boolean sameId(final UUID id, final UUID ignoredId) {
        if (ignoredId == null) {
            return false;
        }
        else {
            return ignoredId.equals(id);
        }
    }
}
