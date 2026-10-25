package org.sure_vote.service;

import java.lang.System.Logger.Level;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.BallotIssuance;
import org.sure_vote.entity.BallotType;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.IssuanceStatus;
import org.sure_vote.entity.RecordingPlace;
import org.sure_vote.entity.RecordingPlaceKind;
import org.sure_vote.repository.SureVoteRepository;

/**
 * Marks a voter's issuance {@code received} after the ballot commits, without a link to the ballot. Mail-in ids
 * go through the queue and are marked in shuffled batches with a whole-day timestamp. In person, a place's rows
 * are marked once its imported ballots match them.
 */
final class ReceivedMarks {
    static final int DEFAULT_BATCH = 25;
    static final int ATTEMPTS = 3;
    static final int DRAIN = 10_000;

    private static final System.Logger LOG = System.getLogger("org.sure_vote.received");

    private final SureVoteRepository repository;
    private final Stores stores;
    private final MailIssuer issuer;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, UUID> pending = new ConcurrentHashMap<>();
    private volatile ReceivedQueue queue = new InMemoryReceivedQueue();
    private volatile int batchSize = DEFAULT_BATCH;

    ReceivedMarks(final SureVoteRepository repository, final Stores stores, final MailIssuer issuer) {
        this.repository = repository;
        this.stores = stores;
        this.issuer = issuer;
    }

    void configure(final ReceivedQueue next, final int size) {
        this.stores.require(next != null, "a received queue is required");
        this.stores.require(size > 0, "the received batch size must be positive");
        this.queue = next;
        this.batchSize = size;
    }

    Optional<UUID> claim(final ImportBallot request) {
        if (mail(request.type())) {
            return Optional.of(this.claimed(request));
        }
        else {
            return Optional.empty();
        }
    }

    void release(final ImportBallot request) {
        this.pending.remove(key(request.personId(), request.electionId()));
    }

    void committed(final ImportBallot request, final Optional<UUID> issued) {
        if (issued.isPresent()) {
            this.queued(issued.get());
        }
        else {
            this.reconcile(request.tenantId(), request.electionId(), request.recordingPlaceId());
        }
    }

    int flush(final boolean force) {
        final var count = new AtomicInteger();
        this.repository.afterCommit(() -> count.set(this.flushNow(force)));
        return count.get();
    }

    private synchronized int flushNow(final boolean force) {
        final var current = this.queue;
        final var marks = current.receive(DRAIN);
        if (marks.isEmpty()) {
            return 0;
        }
        else if (!force && marks.size() < this.batchSize) {
            current.release(marks);
            return 0;
        }
        else {
            return this.marked(current, marks);
        }
    }

    List<PlaceReconciliation> checkedReport(final UUID electionId) {
        this.stores.require(Election.class, electionId);
        return this.report(electionId);
    }

    List<PlaceReconciliation> report(final UUID electionId) {
        final var counts = new LinkedHashMap<UUID, int[]>();
        final var ballots = this.repository.list(Ballot.class);
        final var issuances = this.repository.list(BallotIssuance.class);
        for (final var ballot : ballots) {
            this.countBallot(counts, electionId, ballot);
        }
        for (final var issuance : issuances) {
            this.countIssuance(counts, electionId, issuance);
        }
        return this.reconciliations(counts);
    }

    static Instant coarse(final Instant at) {
        return at.truncatedTo(ChronoUnit.DAYS);
    }

    private static boolean mail(final BallotType type) {
        return type == BallotType.MAIL_IN || type == BallotType.ABSENTEE;
    }

    private UUID claimed(final ImportBallot request) {
        final var key = key(request.personId(), request.electionId());
        this.repository.lock(request.personId());
        return this.guarded(key, this.issuer.requireOutstanding(request.personId(), request.electionId()));
    }

    private UUID guarded(final String key, final UUID id) {
        this.stores.require(this.pending.putIfAbsent(key, id) == null, "This voter's ballot was already received");
        return id;
    }

    private static String key(final UUID personId, final UUID electionId) {
        return personId + ":" + electionId;
    }

    private void queued(final UUID issuanceId) {
        if (this.sent(issuanceId)) {
            this.flush(false);
        }
        else {
            LOG.log(Level.WARNING, "A received mark could not be queued. The issuance stays issued.");
        }
    }

    private boolean sent(final UUID issuanceId) {
        var attempt = 0;
        var delivered = false;
        while (!delivered && attempt < ATTEMPTS) {
            delivered = this.tried(issuanceId);
            attempt = attempt + 1;
        }
        return delivered;
    }

    private boolean tried(final UUID issuanceId) {
        try {
            this.queue.send(issuanceId);
            return true;
        }
        catch (final RuntimeException failure) {
            return false;
        }
    }

    private int marked(final ReceivedQueue current, final List<QueuedMark> marks) {
        final var ids = new ArrayList<UUID>(new LinkedHashSet<UUID>(this.ids(marks)));
        final var day = coarse(Instant.now());
        Collections.shuffle(ids, this.random);
        try {
            final var changed = this.repository.transaction(() -> this.repository.markReceived(ids, day));
            final var values = this.pending.values();
            values.removeAll(ids);
            current.delete(marks);
            return changed;
        }
        catch (final RuntimeException failure) {
            current.release(marks);
            throw failure;
        }
    }

    private List<UUID> ids(final List<QueuedMark> marks) {
        final var ids = new ArrayList<UUID>();
        for (final var mark : marks) {
            ids.add(mark.issuanceId());
        }
        return ids;
    }

    private void reconcile(final UUID tenantId, final UUID electionId, final UUID placeId) {
        this.repository.inTenant(tenantId, () -> this.reconciled(electionId, placeId));
    }

    private Integer reconciled(final UUID electionId, final UUID placeId) {
        final var place = this.stores.require(RecordingPlace.class, placeId);
        final var rows = this.report(electionId);
        final var issued = this.issuedAt(electionId, placeId);
        final var ready = place.getKind() != RecordingPlaceKind.CENTRAL_OFFICE && !issued.isEmpty();
        final var matched = rows
            .stream()
            .anyMatch(row -> placeId.equals(row.recordingPlaceId()) && row.matched());
        if (ready && matched) {
            Collections.shuffle(issued, this.random);
            return this.repository.markReceived(issued, coarse(Instant.now()));
        }
        else {
            return 0;
        }
    }

    private List<UUID> issuedAt(final UUID electionId, final UUID placeId) {
        final var rows = this.repository.list(BallotIssuance.class);
        final var ids = new ArrayList<UUID>();
        for (final var row : rows) {
            this.addIssued(ids, row, electionId, placeId);
        }
        return ids;
    }

    private void addIssued(final List<UUID> ids, final BallotIssuance row, final UUID electionId, final UUID placeId) {
        final var sameElection = electionId.equals(row.getElectionId());
        final var samePlace = placeId.equals(row.getRecordingPlaceId());
        final var issued = row.getStatus() == IssuanceStatus.ISSUED;
        if (sameElection && samePlace && issued && row.getDeletedAt() == null) {
            ids.add(row.getId());
        }
    }

    private void countBallot(final Map<UUID, int[]> counts, final UUID electionId, final Ballot ballot) {
        if (electionId.equals(ballot.getElectionId())) {
            final var count = counts.computeIfAbsent(ballot.getRecordingPlaceId(), key -> new int[3]);
            count[2] = count[2] + 1;
        }
    }

    private void countIssuance(final Map<UUID, int[]> counts, final UUID electionId, final BallotIssuance row) {
        final var sameElection = electionId.equals(row.getElectionId());
        final var status = row.getStatus();
        if (sameElection && row.getDeletedAt() == null && status != null && status.spends()) {
            final var count = counts.computeIfAbsent(row.getRecordingPlaceId(), key -> new int[3]);
            final var slot = status == IssuanceStatus.ISSUED ? 0 : 1;
            count[slot] = count[slot] + 1;
        }
    }

    private List<PlaceReconciliation> reconciliations(final Map<UUID, int[]> counts) {
        final var rows = new ArrayList<PlaceReconciliation>();
        for (final var entry : counts.entrySet()) {
            rows.add(this.reconciliation(entry.getKey(), entry.getValue()));
        }
        return rows;
    }

    private PlaceReconciliation reconciliation(final UUID placeId, final int[] count) {
        final var place = this.stores.require(RecordingPlace.class, placeId);
        final var mail = place.getKind() == RecordingPlaceKind.CENTRAL_OFFICE;
        return new PlaceReconciliation(placeId, mail, count[0], count[1], count[2]);
    }
}
