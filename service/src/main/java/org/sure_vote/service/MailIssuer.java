package org.sure_vote.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.sure_vote.entity.BallotIssuance;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.ElectionStatus;
import org.sure_vote.entity.IssuanceStatus;
import org.sure_vote.entity.Person;
import org.sure_vote.entity.RecordingPlace;
import org.sure_vote.entity.RecordingPlaceKind;
import org.sure_vote.repository.SureVoteRepository;

/**
 * Writes the voter's {@code issued} row when a mail or absentee ballot is sent out, long before it comes back.
 * A replacement voids the outstanding mailed row and issues a new one. Recording a returned ballot writes no
 * issuance row.
 */
final class MailIssuer {
    static final String REPLACED = "replaced by a new mailed ballot";

    private final SureVoteRepository repository;
    private final Stores stores;

    MailIssuer(final SureVoteRepository repository, final Stores stores) {
        this.repository = repository;
        this.stores = stores;
    }

    BallotIssuance mail(final MailIssue request, final UUID actor) {
        return this.repository.transaction(() -> this.mailLocked(request, actor));
    }

    Optional<BallotIssuance> outstanding(final UUID personId, final UUID electionId) {
        final var rows = this.spent(personId, electionId);
        final var stream = rows.stream();
        final var issued = stream.filter(row -> row.getStatus() == IssuanceStatus.ISSUED);
        return issued.findFirst();
    }

    UUID requireOutstanding(final UUID personId, final UUID electionId) {
        final var rows = this.spent(personId, electionId);
        if (rows.isEmpty()) {
            throw new ConstraintException("No mailed ballot is outstanding for this voter");
        }
        else {
            return this.requireIssued(rows.getFirst());
        }
    }

    private UUID requireIssued(final BallotIssuance row) {
        if (row.getStatus() == IssuanceStatus.RECEIVED) {
            throw new ConstraintException("This voter's ballot was already received");
        }
        else {
            return this.requireMailed(row);
        }
    }

    private UUID requireMailed(final BallotIssuance row) {
        final var place = this.stores.require(RecordingPlace.class, row.getRecordingPlaceId());
        this.stores.require(
            place.getKind() == RecordingPlaceKind.CENTRAL_OFFICE,
            "This voter was issued a ballot in person"
        );
        return row.getId();
    }

    private BallotIssuance mailLocked(final MailIssue request, final UUID actor) {
        final var tenantId = request.tenantId();
        final var election = this.stores.require(Election.class, request.electionId());
        final var person = this.stores.require(Person.class, request.personId());
        final var place = this.stores.require(RecordingPlace.class, request.recordingPlaceId());
        final var status = election.getStatus();
        final var open = status == ElectionStatus.UPCOMING || status == ElectionStatus.ACTIVE;
        this.stores.sameTenant(tenantId, election.getTenantId(), "Election");
        this.stores.sameTenant(tenantId, person.getTenantId(), "Person");
        this.stores.sameTenant(tenantId, place.getTenantId(), "Recording place");
        this.stores.requireActiveTenant(tenantId);
        this.stores.require(open, "Mail ballots are sent before the election closes");
        this.stores.require(person.getDeletedAt() == null, "Person is deleted");
        this.stores.require(
            place.getKind() == RecordingPlaceKind.CENTRAL_OFFICE,
            "A mail ballot is issued from a central office"
        );
        this.replaced(request, actor);
        return this.stores.add(this.issuance(request), actor);
    }

    private void replaced(final MailIssue request, final UUID actor) {
        final var rows = this.spent(request.personId(), request.electionId());
        if (!rows.isEmpty()) {
            this.replace(rows.getFirst(), request.replace(), actor);
        }
    }

    private void replace(final BallotIssuance existing, final boolean replace, final UUID actor) {
        final var place = this.stores.require(RecordingPlace.class, existing.getRecordingPlaceId());
        final var mailed = place.getKind() == RecordingPlaceKind.CENTRAL_OFFICE;
        this.stores.require(existing.getStatus() == IssuanceStatus.ISSUED, "This voter's ballot was already received");
        this.stores.require(mailed, "This voter was issued a ballot in person");
        this.stores.require(replace, "Voter already has an issued ballot");
        this.voided(existing, actor);
    }

    private void voided(final BallotIssuance existing, final UUID actor) {
        final var next = Copies.issuance(existing);
        next.setStatus(IssuanceStatus.VOIDED);
        next.setVoidReason(REPLACED);
        next.setUpdatedAt(Instant.now());
        next.setUpdatedBy(actor);
        next.setVersion(this.stores.nextVersion(existing.getVersion()));
        this.stores.replace(next);
    }

    private BallotIssuance issuance(final MailIssue request) {
        final var issuance = new BallotIssuance();
        issuance.setTenantId(request.tenantId());
        issuance.setPersonId(request.personId());
        issuance.setElectionId(request.electionId());
        issuance.setRecordingPlaceId(request.recordingPlaceId());
        issuance.setStatus(IssuanceStatus.ISSUED);
        issuance.setAcceptedAt(Instant.now());
        return issuance;
    }

    private List<BallotIssuance> spent(final UUID personId, final UUID electionId) {
        final var rows = this.repository.list(BallotIssuance.class);
        final var stream = rows.stream();
        final var matching = stream.filter(row -> this.spends(row, personId, electionId));
        return matching.toList();
    }

    private boolean spends(final BallotIssuance row, final UUID personId, final UUID electionId) {
        final var status = row.getStatus();
        final var samePerson = personId.equals(row.getPersonId());
        final var sameElection = electionId.equals(row.getElectionId());
        return samePerson && sameElection && row.getDeletedAt() == null && status != null && status.spends();
    }
}
