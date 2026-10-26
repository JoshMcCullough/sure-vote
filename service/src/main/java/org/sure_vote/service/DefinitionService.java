package org.sure_vote.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.sure_vote.entity.Address;
import org.sure_vote.entity.BallotIssuance;
import org.sure_vote.entity.BallotStyle;
import org.sure_vote.entity.Candidate;
import org.sure_vote.entity.ChainStatus;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.ElectionStatus;
import org.sure_vote.entity.ElectionVotingPrecinctAssignment;
import org.sure_vote.entity.IssuanceStatus;
import org.sure_vote.entity.Locality;
import org.sure_vote.entity.Person;
import org.sure_vote.entity.PrecinctDistrict;
import org.sure_vote.entity.Race;
import org.sure_vote.entity.RaceCandidate;
import org.sure_vote.entity.RecordingDevice;
import org.sure_vote.entity.RecordingPlace;
import org.sure_vote.entity.Seat;
import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.TenantKind;
import org.sure_vote.entity.TenantValidator;
import org.sure_vote.entity.Term;
import org.sure_vote.entity.VoteChain;
import org.sure_vote.entity.VoteChainValidator;
import org.sure_vote.entity.VotingDistrict;
import org.sure_vote.entity.VotingPrecinct;
import org.sure_vote.repository.SureVoteRepository;

final class DefinitionService {
    private final SureVoteRepository repository;
    private final Stores stores;
    private final ElectionDefinitions definitions;

    DefinitionService(
        final SureVoteRepository repository,
        final Stores stores,
        final ElectionDefinitions definitions
    ) {
        this.repository = repository;
        this.stores = stores;
        this.definitions = definitions;
    }

    Person createPerson(final Person person, final UUID actor) {
        final var tenant = this.stores.require(Tenant.class, person.getTenantId());
        this.stores.requireActiveTenant(person.getTenantId());
        this.rejectShowcase(tenant, person);
        this.rejectPlaintext(person);
        return this.stores.add(person, actor);
    }

    Locality createLocality(final Locality locality, final UUID actor) {
        return this.stores.add(locality, actor);
    }

    Address createAddress(final Address address, final UUID actor) {
        final var locality = this.stores.require(Locality.class, address.getLocalityId());
        this.stores.sameTenant(address.getTenantId(), locality.getTenantId(), "Locality");
        return this.stores.add(address, actor);
    }

    VotingDistrict createVotingDistrict(final VotingDistrict district, final UUID actor) {
        final var locality = this.stores.require(Locality.class, district.getLocalityId());
        this.stores.sameTenant(district.getTenantId(), locality.getTenantId(), "Locality");
        return this.stores.add(district, actor);
    }

    VotingPrecinct createPrecinct(final VotingPrecinct precinct, final UUID actor) {
        final var address = this.stores.require(Address.class, precinct.getAddressId());
        this.stores.sameTenant(precinct.getTenantId(), address.getTenantId(), "Address");
        return this.stores.add(precinct, actor);
    }

    PrecinctDistrict createPrecinctDistrict(final PrecinctDistrict row, final UUID actor) {
        final var precinct = this.stores.require(VotingPrecinct.class, row.getVotingPrecinctId());
        this.stores.sameTenant(row.getTenantId(), precinct.getTenantId(), "Precinct");
        return this.stores.add(row, actor);
    }

    Seat createSeat(final Seat seat, final UUID actor) {
        final var locality = this.stores.require(Locality.class, seat.getLocalityId());
        this.stores.sameTenant(seat.getTenantId(), locality.getTenantId(), "Locality");
        this.stores.require(seat.getAppointments() >= 1, "appointments must be at least 1");
        return this.stores.add(seat, actor);
    }

    Term createTerm(final Term term, final UUID actor) {
        final var seat = this.stores.require(Seat.class, term.getSeatId());
        final var start = term.getTermStart();
        final var end = term.getTermEnd();
        final var ordered = start == null || end == null || !start.isAfter(end);
        this.stores.sameTenant(term.getTenantId(), seat.getTenantId(), "Seat");
        this.stores.require(ordered, "a term ends on or after it starts");
        this.stores.require(!this.overlaps(term), "terms for a seat may not overlap");
        return this.stores.add(term, actor);
    }

    private boolean overlaps(final Term term) {
        final var terms = this.repository.list(Term.class);
        var found = false;
        for (final var existing : terms) {
            found = found || this.overlap(existing, term);
        }
        return found;
    }

    private boolean overlap(final Term existing, final Term term) {
        final var seat = term.getSeatId();
        final var sameSeat = seat.equals(existing.getSeatId());
        final var live = existing.getDeletedAt() == null;
        final var dated = this.dated(existing) && this.dated(term);
        if (sameSeat && live && dated) {
            return this.intersects(existing.getTermStart(), existing.getTermEnd(), term);
        }
        else {
            return false;
        }
    }

    private boolean intersects(final LocalDate start, final LocalDate end, final Term term) {
        final var otherStart = term.getTermStart();
        return !start.isAfter(term.getTermEnd()) && !otherStart.isAfter(end);
    }

    private boolean dated(final Term term) {
        return term.getTermStart() != null && term.getTermEnd() != null;
    }

    Election createElection(final Election election, final UUID actor) {
        final var locality = this.stores.require(Locality.class, election.getLocalityId());
        final var start = election.getVotingStart();
        final var end = election.getVotingEnd();
        final var window = start != null && end != null && !start.isAfter(end);
        this.stores.sameTenant(election.getTenantId(), locality.getTenantId(), "Locality");
        this.stores.requireActiveTenant(election.getTenantId());
        this.stores.require(window, "voting window is invalid");
        this.stores.require(Signatures.isPublicKey(election.getOperatorPublicKey()), "operator public key");
        this.stores.require(Signatures.isPublicKey(election.getImportPublicKey()), "import public key");
        this.stores.require(Signatures.isPublicKey(election.getViewingPublicKey()), "viewing public key");
        election.setStatus(ElectionStatus.UPCOMING);
        election.setHashAlgorithm("SHA-256");
        election.setBallotHashPayloadSchema("canonical-json-v1");
        election.setVoteHashPayloadSchema("canonical-json-v1");
        election.setVoteChainEntryHashPayloadSchema("canonical-json-v1");
        election.setHash(null);
        election.setSignature(null);
        return this.stores.add(election, actor);
    }

    Race createRace(final Race race, final UUID actor) {
        return this.repository.transaction(() -> this.createRaceLocked(race, actor));
    }

    Race updateRace(final RaceEdit edit, final UUID actor) {
        return this.repository.transaction(() -> this.updateRaceLocked(edit, actor));
    }

    Candidate createCandidate(final Candidate candidate, final UUID actor) {
        final var person = this.stores.require(Person.class, candidate.getPersonId());
        this.stores.sameTenant(candidate.getTenantId(), person.getTenantId(), "Person");
        return this.stores.add(candidate, actor);
    }

    RaceCandidate createRaceCandidate(final RaceCandidate slot, final UUID actor) {
        final var race = this.stores.require(Race.class, slot.getRaceId());
        final var candidate = this.stores.require(Candidate.class, slot.getCandidateId());
        final var election = this.stores.require(Election.class, race.getElectionId());
        this.stores.sameTenant(slot.getTenantId(), race.getTenantId(), "Race");
        this.stores.sameTenant(slot.getTenantId(), candidate.getTenantId(), "Candidate");
        this.requireUpcoming(election, "Candidates lock when the election is activated");
        return this.stores.add(slot, actor);
    }

    BallotStyle createBallotStyle(final BallotStyle style, final UUID actor) {
        final var election = this.stores.require(Election.class, style.getElectionId());
        final var precinct = this.stores.require(VotingPrecinct.class, style.getVotingPrecinctId());
        final var race = this.stores.require(Race.class, style.getRaceId());
        final var raceElection = race.getElectionId();
        final var electionId = election.getId();
        final var sameElection = electionId.equals(raceElection);
        this.stores.sameTenant(style.getTenantId(), election.getTenantId(), "Election");
        this.stores.sameTenant(style.getTenantId(), precinct.getTenantId(), "Precinct");
        this.stores.require(sameElection, "The race is in another election");
        this.requireUpcoming(election, "Ballot style locks when the election is activated");
        return this.stores.add(style, actor);
    }

    RecordingPlace createRecordingPlace(final RecordingPlace place, final UUID actor) {
        final var locality = this.stores.require(Locality.class, place.getLocalityId());
        this.stores.sameTenant(place.getTenantId(), locality.getTenantId(), "Locality");
        return this.stores.add(place, actor);
    }

    RecordingDevice createRecordingDevice(final RecordingDevice device, final UUID actor) {
        this.stores.require(Signatures.isPublicKey(device.getPublicKey()), "device public key");
        this.requireDevicesUnlocked(device.getTenantId());
        return this.stores.add(device, actor);
    }

    ElectionVotingPrecinctAssignment assignPrecinct(
        final ElectionVotingPrecinctAssignment assignment,
        final UUID actor
    ) {
        final var election = this.stores.require(Election.class, assignment.getElectionId());
        final var precinct = this.stores.require(VotingPrecinct.class, assignment.getVotingPrecinctId());
        this.stores.sameTenant(assignment.getTenantId(), election.getTenantId(), "Election");
        this.stores.sameTenant(assignment.getTenantId(), precinct.getTenantId(), "Precinct");
        this.requireUpcoming(election, "Precinct assignments lock when the election is activated");
        return this.stores.add(assignment, actor);
    }

    BallotIssuance acceptVoter(final BallotIssuance issuance, final UUID actor) {
        final var election = this.stores.require(Election.class, issuance.getElectionId());
        final var person = this.stores.require(Person.class, issuance.getPersonId());
        final var place = this.stores.require(RecordingPlace.class, issuance.getRecordingPlaceId());
        this.stores.sameTenant(issuance.getTenantId(), election.getTenantId(), "Election");
        this.stores.sameTenant(issuance.getTenantId(), person.getTenantId(), "Person");
        this.stores.sameTenant(issuance.getTenantId(), place.getTenantId(), "Recording place");
        this.stores.requireActiveTenant(election.getTenantId());
        this.stores.require(
            election.getStatus() == ElectionStatus.ACTIVE,
            "Voters are accepted while the election is active"
        );
        issuance.setStatus(IssuanceStatus.ISSUED);
        issuance.setAcceptedAt(Instant.now());
        return this.stores.add(issuance, actor);
    }

    BallotIssuance voidIssuance(final UUID id, final String reason, final UUID actor) {
        final var existing = this.stores.require(BallotIssuance.class, id);
        final var blank = reason == null || reason.isBlank();
        this.stores.require(existing.getStatus() == IssuanceStatus.ISSUED, "Only an issued ballot can be voided");
        this.stores.require(!blank, "A void reason is required");
        return this.voided(existing, reason, actor);
    }

    String definitionHash(final UUID electionId) {
        final var election = this.stores.require(Election.class, electionId);
        final var payload = this.definitions.payload(this.repository, election);
        this.definitions.validate(this.repository, election);
        return Digests.sha256Hex(payload);
    }

    Election activate(final UUID electionId, final String signature, final UUID actor) {
        final var existing = this.stores.require(Election.class, electionId);
        final var payload = this.definitions.payload(this.repository, existing);
        final var hash = Digests.sha256Hex(payload);
        final var raw = Digests.unhex(hash);
        final var signed = Signatures.verify(existing.getOperatorPublicKey(), raw, signature);
        this.stores.require(
            existing.getStatus() == ElectionStatus.UPCOMING,
            "Only an upcoming election can be activated"
        );
        this.stores.requireActiveTenant(existing.getTenantId());
        this.definitions.validate(this.repository, existing);
        this.stores.require(signed, "Operator signature does not match the election definition");
        this.requireOutsideValidator(existing);
        return this.activated(existing, hash, signature, actor);
    }

    Election election(final UUID id) {
        return this.stores.require(Election.class, id);
    }

    boolean demonstration(final UUID electionId) {
        final var election = this.stores.require(Election.class, electionId);
        final var tenant = this.stores.require(Tenant.class, election.getTenantId());
        return tenant.getKind() == TenantKind.SHOWCASE;
    }

    /**
     * Workstation registration. The host API does not call this.
     * The stored value is the sealed ciphertext, never the plaintext.
     */
    Person sealViewingPassphrase(
        final UUID personId,
        final String plaintext,
        final String viewingPublicKey,
        final UUID actor
    ) {
        final var person = this.stores.require(Person.class, personId);
        this.stores.require(actor != null, "actor is required");
        this.stores.requireActiveTenant(person.getTenantId());
        this.stores.require(Signatures.isPublicKey(viewingPublicKey), "viewing public key");
        this.stores.require(person.getViewingPassphrase() == null, "viewing passphrase is already sealed");
        return this.storePassphrase(person, plaintext, viewingPublicKey, actor);
    }

    private Race createRaceLocked(final Race race, final UUID actor) {
        final var election = this.stores.require(Election.class, race.getElectionId());
        this.stores.sameTenant(race.getTenantId(), election.getTenantId(), "Race");
        this.requireUpcoming(election, "Races lock when the election is activated");
        this.requireLineage(race);
        return this.saveRace(race, actor);
    }

    private Race updateRaceLocked(final RaceEdit edit, final UUID actor) {
        final var existing = this.stores.require(Race.class, edit.raceId());
        final var election = this.stores.require(Election.class, existing.getElectionId());
        final var next = Copies.race(existing);
        final var raceId = edit.raceId();
        this.requireUpcoming(election, "Races lock when the election is activated");
        this.stores.require(!raceId.equals(edit.previousRaceId()), "A race cannot follow itself");
        this.edited(next, edit);
        this.requireLineage(next);
        this.requireSuccessors(next);
        next.setUpdatedAt(Instant.now());
        next.setUpdatedBy(actor);
        next.setVersion(this.stores.nextVersion(existing.getVersion()));
        return this.stores.replace(next);
    }

    private void edited(final Race race, final RaceEdit edit) {
        race.setName(Objects.requireNonNullElse(edit.name(), race.getName()));
        race.setMethod(Objects.requireNonNullElse(edit.method(), race.getMethod()));
        race.setPreviousRaceId(edit.previousRaceId());
        race.setRound(0);
    }

    private void requireSuccessors(final Race race) {
        final var races = this.repository.list(Race.class);
        for (final var later : races) {
            this.requireSuccessor(race, later);
        }
    }

    private void requireSuccessor(final Race race, final Race later) {
        final var follows = Objects.equals(race.getId(), later.getPreviousRaceId());
        final var fits = later.getRound() == race.getRound() + 1 && later.getMethod() == race.getMethod();
        this.stores.require(!follows || fits, "A later round no longer follows this race");
    }

    private void requireLineage(final Race race) {
        final var previousId = race.getPreviousRaceId();
        if (previousId == null) {
            this.stores.require(race.getRound() == 0 || race.getRound() == 1, "The first round of a race is 1");
            race.setRound(1);
        }
        else {
            this.followPrevious(race, this.stores.require(Race.class, previousId));
        }
    }

    private void followPrevious(final Race race, final Race previous) {
        final var round = previous.getRound() + 1;
        final var given = race.getRound();
        this.stores.sameTenant(race.getTenantId(), previous.getTenantId(), "Previous race");
        this.stores.require(given == 0 || given == round, "A race round follows its previous race");
        this.stores.require(race.getMethod() == previous.getMethod(), "A race keeps the method of its previous race");
        race.setRound(round);
    }

    private Race saveRace(final Race race, final UUID actor) {
        final var saved = this.stores.add(race, actor);
        final var chain = new VoteChain();
        final var payload = Payloads.emptyHead(saved.getId());
        final var hash = Digests.sha256Hex(payload);
        chain.setTenantId(saved.getTenantId());
        chain.setRaceId(saved.getId());
        chain.setStatus(ChainStatus.ACTIVE);
        chain.setLength(0);
        chain.setHash(hash);
        this.stores.add(chain, actor);
        return saved;
    }

    private void requireOutsideValidator(final Election election) {
        final var tenant = this.stores.require(Tenant.class, election.getTenantId());
        if (tenant.getKind() == TenantKind.PRODUCTION) {
            final var registered = this.outsideValidator(tenant.getId());
            this.stores.require(registered, "a production election needs an outside validator");
        }
    }

    private boolean outsideValidator(final UUID tenantId) {
        final var links = this.repository.list(TenantValidator.class);
        return this.liveLink(links, tenantId);
    }

    private boolean liveLink(final List<TenantValidator> links, final UUID tenantId) {
        var found = false;
        for (final var link : links) {
            found = found || this.live(link, tenantId);
        }
        return found;
    }

    private boolean live(final TenantValidator link, final UUID tenantId) {
        final var kept = link.getDeletedAt() == null;
        final var same = tenantId.equals(link.getTenantId());
        if (kept && same) {
            return this.liveValidator(link.getValidatorId());
        }
        else {
            return false;
        }
    }

    private boolean liveValidator(final UUID validatorId) {
        final var found = this.repository.find(VoteChainValidator.class, validatorId);
        if (found.isEmpty()) {
            return false;
        }
        else {
            final var validator = found.get();
            final var kept = validator.getDeletedAt() == null;
            return kept;
        }
    }

    private Election activated(final Election existing, final String hash, final String signature, final UUID actor) {
        final var next = Copies.election(existing);
        next.setHash(hash);
        next.setSignature(signature);
        next.setStatus(ElectionStatus.ACTIVE);
        next.setUpdatedAt(Instant.now());
        next.setUpdatedBy(actor);
        next.setVersion(this.stores.nextVersion(existing.getVersion()));
        return this.stores.replace(next);
    }

    private BallotIssuance voided(final BallotIssuance existing, final String reason, final UUID actor) {
        final var next = Copies.issuance(existing);
        next.setStatus(IssuanceStatus.VOIDED);
        next.setVoidReason(reason);
        next.setUpdatedAt(Instant.now());
        next.setUpdatedBy(actor);
        next.setVersion(this.stores.nextVersion(existing.getVersion()));
        return this.stores.replace(next);
    }

    private Person storePassphrase(
        final Person person,
        final String plaintext,
        final String viewingPublicKey,
        final UUID actor
    ) {
        final var normalized = Passphrases.require(plaintext, person);
        final var password = normalized.getBytes(StandardCharsets.UTF_8);
        try {
            return this.replacePassphrase(person, password, viewingPublicKey, actor);
        }
        finally {
            ViewingBoxes.wipe(password);
        }
    }

    private Person replacePassphrase(
        final Person person,
        final byte[] password,
        final String viewingPublicKey,
        final UUID actor
    ) {
        final var sealed = ViewingBoxes.seal(password, Digests.unhex(viewingPublicKey));
        final var stored = Digests.hex(sealed);
        final var next = Copies.person(person);
        final var version = this.stores.nextVersion(person.getVersion());
        next.setViewingPassphrase(stored);
        next.setUpdatedAt(Instant.now());
        next.setUpdatedBy(actor);
        next.setVersion(version);
        return this.stores.replace(next);
    }

    private void rejectPlaintext(final Person person) {
        if (person.getViewingPassphrase() != null) {
            throw new ConstraintException("viewing passphrase is stored only as a sealed box");
        }
    }

    private void rejectShowcase(final Tenant tenant, final Person person) {
        final var showcase = tenant.getKind() == TenantKind.SHOWCASE;
        final var secret = person.getSsn() != null || person.getVoterId() != null;
        if (showcase && secret) {
            throw new ConstraintException("A showcase tenant rejects ssn and voter id");
        }
    }

    private void requireUpcoming(final Election election, final String message) {
        this.stores.require(election.getStatus() == ElectionStatus.UPCOMING, message);
    }

    private void requireDevicesUnlocked(final UUID tenantId) {
        final var elections = this.repository.list(Election.class);
        for (final var election : elections) {
            this.rejectLockedDevice(tenantId, election);
        }
    }

    private void rejectLockedDevice(final UUID tenantId, final Election election) {
        final var electionTenant = election.getTenantId();
        final var same = tenantId.equals(electionTenant);
        final var locked = election.getStatus() != ElectionStatus.UPCOMING;
        if (same && locked) {
            throw new ConstraintException("Devices lock when an election is activated");
        }
    }
}
