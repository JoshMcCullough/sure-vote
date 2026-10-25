package org.sure_vote.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.sure_vote.entity.BallotIssuance;
import org.sure_vote.entity.BallotType;
import org.sure_vote.entity.IssuanceStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceivedMarksTest {
    private static final List<IssuanceStatus> ONE_ISSUED = List.of(IssuanceStatus.ISSUED);
    private static final List<IssuanceStatus> TWO_ISSUED = List.of(IssuanceStatus.ISSUED, IssuanceStatus.ISSUED);
    private static final List<IssuanceStatus> TWO_RECEIVED = List.of(
        IssuanceStatus.RECEIVED,
        IssuanceStatus.RECEIVED
    );

    @Test
    void mailIssuancesStayIssuedUntilABatchFills() {
        final var world = SampleElection.hostedMail();
        final var queue = this.configured(world, new InMemoryReceivedQueue(), 3);
        final var waiting = this.mailThenStatuses(world, 2);
        final var queued = queue.size();
        final var marked = this.mailThenStatuses(world, 1);
        assertEquals(TWO_ISSUED, waiting);
        assertEquals(2, queued);
        assertEquals(List.of(IssuanceStatus.RECEIVED, IssuanceStatus.RECEIVED, IssuanceStatus.RECEIVED), marked);
        assertEquals(0, queue.size());
        this.assertCoarse(world);
    }

    @Test
    void theFlushOrderDoesNotChangeTheRows() {
        final var world = SampleElection.hostedMail();
        final var held = this.configured(world, new InMemoryReceivedQueue(), 100);
        final var ids = this.mail(world, 4);
        final var before = this.rows(world);
        final var first = this.flushIds(world, List.of(ids.get(3), ids.get(2)));
        final var second = this.flushIds(world, List.of(ids.get(0), ids.get(1)));
        final var after = this.rows(world);
        final var day = this.receivedAt(after.getFirst());
        assertEquals(4, held.size());
        assertEquals(2, first);
        assertEquals(2, second);
        for (final var row : after) {
            this.assertOnlyMarked(this.byId(before, row.getId()), row, day);
        }
    }

    @Test
    void markingIsIdempotent() {
        final var world = SampleElection.hostedMail();
        final var queue = this.configured(world, new InMemoryReceivedQueue(), 100);
        final var ids = this.mail(world, 2);
        final var marked = world.app.flushReceived();
        final var once = this.rows(world);
        final var redelivered = this.flushIds(world, List.of(ids.getFirst(), ids.getFirst()));
        final var day = ReceivedMarks.coarse(Instant.now());
        final var again = world.repository.transaction(() -> world.repository.markReceived(ids, day));
        final var twice = this.rows(world);
        assertEquals(2, marked);
        assertEquals(0, redelivered);
        assertEquals(0, again);
        assertEquals(0, queue.size());
        for (final var row : twice) {
            this.assertOnlyMarked(this.byId(once, row.getId()), row, this.receivedAt(row));
        }
    }

    @Test
    void aFailedSendLeavesTheIssuanceIssuedAndStillSpent() {
        final var world = SampleElection.hostedMail();
        final var queue = this.configured(world, new FailingQueue(), 1);
        final var personId = world.person("Ada", "Voter");
        final var ballot = world.app.importBallot(world.sealedMail(BallotType.MAIL_IN, personId, world.actor));
        final var rows = this.rows(world);
        final var row = rows.getFirst();
        final var again = world.sealedMail(BallotType.MAIL_IN, personId, world.actor);
        final var failure = assertThrows(ConstraintException.class, () -> world.app.importBallot(again));
        assertEquals(BallotType.MAIL_IN, ballot.getType());
        assertEquals(ReceivedMarks.ATTEMPTS, queue.attempts);
        assertEquals(1, rows.size());
        assertEquals(IssuanceStatus.ISSUED, row.getStatus());
        assertNull(row.getReceivedAt());
        assertEquals("Voter already has an issued ballot", failure.getMessage());
    }

    @Test
    void aSendThatRecoversIsQueued() {
        final var world = SampleElection.hostedMail();
        final var queue = this.configured(world, new FlakyQueue(), 1);
        final var statuses = this.mailThenStatuses(world, 1);
        assertTrue(queue.failed);
        assertEquals(List.of(IssuanceStatus.RECEIVED), statuses);
    }

    @Test
    void aFlushInsideARolledBackTransactionMarksNothing() {
        final var world = SampleElection.hostedMail();
        final var queue = this.configured(world, new InMemoryReceivedQueue(), 100);
        final var ids = this.mail(world, 1);
        final var failure = assertThrows(
            IllegalStateException.class,
            () -> world.repository.transaction(() -> this.flushThenFail(world))
        );
        final var statuses = this.statuses(world);
        final var waiting = queue.size();
        final var flushed = world.app.flushReceived();
        assertEquals(1, ids.size());
        assertEquals("rolled back", failure.getMessage());
        assertEquals(ONE_ISSUED, statuses);
        assertEquals(1, waiting);
        assertEquals(1, flushed);
    }

    @Test
    void closingFlushesAPartialBatch() {
        final var world = SampleElection.hostedMail();
        final var queue = this.configured(world, new InMemoryReceivedQueue(), 25);
        final var before = this.mailThenStatuses(world, 2);
        final var batch = world.app.flushReceivedBatch();
        final var after = this.closedStatuses(world);
        assertEquals(TWO_ISSUED, before);
        assertEquals(0, batch);
        assertEquals(TWO_RECEIVED, after);
        assertEquals(0, queue.size());
    }

    @Test
    void anEmptyQueueFlushesNothing() {
        final var world = SampleElection.hostedMail();
        final var empty = new InMemoryReceivedQueue();
        assertEquals(0, world.app.flushReceived());
        assertThrows(ConstraintException.class, () -> world.app.configureReceivedQueue(null, 1));
        assertThrows(ConstraintException.class, () -> world.app.configureReceivedQueue(empty, 0));
    }

    @Test
    void aPartialDayIsRefused() {
        final var world = SampleElection.hostedMail();
        final var ids = this.mail(world, 1);
        final var precise = Instant.parse("2026-11-03T12:34:56Z");
        assertThrows(
            RuntimeException.class,
            () -> world.repository.transaction(() -> world.repository.markReceived(ids, precise))
        );
        assertEquals(ONE_ISSUED, this.statuses(world));
    }

    @Test
    void inPersonRowsAreReceivedWhenThePlaceMatches() {
        final var world = SampleElection.fptp();
        final var issued = this.accepted(world, 2);
        final var partial = this.castThenStatuses(world);
        final var waiting = world.app.reconciliation(world.electionId);
        final var matched = this.castThenStatuses(world);
        final var report = world.app.reconciliation(world.electionId);
        final var row = report.getFirst();
        final var early = waiting.getFirst();
        assertEquals(2, issued.size());
        assertEquals(TWO_ISSUED, partial);
        assertFalse(early.matched());
        assertEquals(TWO_RECEIVED, matched);
        assertEquals(1, report.size());
        assertEquals(world.placeId, row.recordingPlaceId());
        assertEquals(0, row.issued());
        assertEquals(2, row.received());
        assertEquals(2, row.ballots());
        assertTrue(row.matched());
        this.assertCoarse(world);
    }

    @Test
    void theReportCoversEveryPlace() {
        final var world = SampleElection.hostedMail();
        final var queue = this.configured(world, new InMemoryReceivedQueue(), 100);
        final var inPerson = this.accepted(world, 1);
        final var ballot = world.castOne(world.slotA, "Y");
        final var mailed = this.mail(world, 2);
        final var report = world.app.reconciliation(world.electionId);
        final var office = this.place(report, world.officeId);
        final var polls = this.place(report, world.placeId);
        final var missing = UUID.randomUUID();
        assertEquals(1, inPerson.size());
        assertEquals(BallotType.IN_PERSON, ballot.getType());
        assertEquals(2, mailed.size());
        assertEquals(2, queue.size());
        assertEquals(2, report.size());
        assertEquals(2, office.issued());
        assertEquals(0, office.received());
        assertEquals(2, office.ballots());
        assertTrue(office.matched());
        assertEquals(1, polls.received());
        assertThrows(ConstraintException.class, () -> world.app.reconciliation(missing));
    }

    @Test
    void aVoidedIssuanceIsNotCounted() {
        final var world = SampleElection.fptp();
        final var issuances = this.accepted(world, 1);
        final var voided = world.app.voidIssuance(issuances.getFirst(), "left the line", world.actor);
        final var ballot = world.castOne(world.slotA, "Y");
        final var report = world.app.reconciliation(world.electionId);
        final var row = report.getFirst();
        assertEquals(IssuanceStatus.VOIDED, voided.getStatus());
        assertEquals(BallotType.IN_PERSON, ballot.getType());
        assertEquals(0, row.issued());
        assertEquals(0, row.received());
        assertEquals(1, row.ballots());
        assertFalse(row.matched());
    }

    private <Q extends ReceivedQueue> Q configured(final SampleElection world, final Q queue, final int size) {
        world.app.configureReceivedQueue(queue, size);
        return queue;
    }

    private int flushIds(final SampleElection world, final List<UUID> ids) {
        final var queue = new InMemoryReceivedQueue();
        for (final var id : ids) {
            queue.send(id);
        }
        world.app.configureReceivedQueue(queue, 100);
        return world.app.flushReceived();
    }

    private Integer flushThenFail(final SampleElection world) {
        world.app.flushReceived();
        throw new IllegalStateException("rolled back");
    }

    private List<IssuanceStatus> mailThenStatuses(final SampleElection world, final int count) {
        this.mail(world, count);
        return this.statuses(world);
    }

    private List<IssuanceStatus> castThenStatuses(final SampleElection world) {
        world.castOne(world.slotA, "Y");
        return this.statuses(world);
    }

    private List<IssuanceStatus> closedStatuses(final SampleElection world) {
        world.close();
        return this.statuses(world);
    }

    private List<UUID> accepted(final SampleElection world, final int count) {
        final var ids = new ArrayList<UUID>();
        for (var index = 0; index < count; index++) {
            ids.add(world.accept(world.person("Polls", "Voter " + index)));
        }
        return ids;
    }

    private List<UUID> mail(final SampleElection world, final int count) {
        final var ids = new ArrayList<UUID>();
        for (var index = 0; index < count; index++) {
            ids.add(this.mailOne(world, index));
        }
        return ids;
    }

    private UUID mailOne(final SampleElection world, final int index) {
        final var personId = world.person("Mail", "Voter " + index);
        final var sealed = world.sealedMail(BallotType.MAIL_IN, personId, world.actor);
        world.app.importBallot(sealed);
        return this.issuanceFor(world, personId);
    }

    private UUID issuanceFor(final SampleElection world, final UUID personId) {
        final var rows = this.rows(world);
        final var stream = rows.stream();
        final var matching = stream.filter(row -> personId.equals(row.getPersonId()));
        final var found = matching.findFirst();
        final var row = found.orElseThrow();
        return row.getId();
    }

    private List<BallotIssuance> rows(final SampleElection world) {
        return world.repository.list(BallotIssuance.class);
    }

    private List<IssuanceStatus> statuses(final SampleElection world) {
        final var rows = this.rows(world);
        final var stream = rows.stream();
        final var mapped = stream.map(BallotIssuance::getStatus);
        return mapped.toList();
    }

    private Instant receivedAt(final BallotIssuance row) {
        return row.getReceivedAt();
    }

    private BallotIssuance byId(final List<BallotIssuance> rows, final UUID id) {
        final var stream = rows.stream();
        final var matching = stream.filter(row -> id.equals(row.getId()));
        final var found = matching.findFirst();
        return found.orElseThrow();
    }

    private PlaceReconciliation place(final List<PlaceReconciliation> rows, final UUID placeId) {
        final var stream = rows.stream();
        final var matching = stream.filter(row -> placeId.equals(row.recordingPlaceId()));
        final var found = matching.findFirst();
        return found.orElseThrow();
    }

    private void assertOnlyMarked(final BallotIssuance earlier, final BallotIssuance row, final Instant day) {
        assertEquals(IssuanceStatus.RECEIVED, row.getStatus());
        assertEquals(day, row.getReceivedAt());
        assertEquals(earlier.getUpdatedAt(), row.getUpdatedAt());
        assertEquals(earlier.getVersion(), row.getVersion());
    }

    private void assertCoarse(final SampleElection world) {
        for (final var row : this.rows(world)) {
            final var at = row.getReceivedAt();
            assertEquals(at.truncatedTo(ChronoUnit.DAYS), at);
        }
    }

    private static final class FailingQueue implements ReceivedQueue {
        private int attempts;

        @Override
        public void send(final UUID issuanceId) {
            this.attempts = this.attempts + 1;
            throw new IllegalStateException("queue unavailable");
        }

        @Override
        public List<QueuedMark> receive(final int max) {
            return List.of();
        }

        @Override
        public void delete(final List<QueuedMark> marks) {
        }

        @Override
        public void release(final List<QueuedMark> marks) {
        }
    }

    private static final class FlakyQueue implements ReceivedQueue {
        private final InMemoryReceivedQueue inner = new InMemoryReceivedQueue();
        private boolean failed;

        @Override
        public void send(final UUID issuanceId) {
            if (this.failed) {
                this.inner.send(issuanceId);
            }
            else {
                this.failed = true;
                throw new IllegalStateException("queue unavailable");
            }
        }

        @Override
        public List<QueuedMark> receive(final int max) {
            return this.inner.receive(max);
        }

        @Override
        public void delete(final List<QueuedMark> marks) {
            this.inner.delete(marks);
        }

        @Override
        public void release(final List<QueuedMark> marks) {
            this.inner.release(marks);
        }
    }
}
