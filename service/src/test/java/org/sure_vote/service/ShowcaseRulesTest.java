package org.sure_vote.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.sure_vote.entity.Address;
import org.sure_vote.entity.AssignmentStatus;
import org.sure_vote.entity.BallotStyle;
import org.sure_vote.entity.Candidate;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.ElectionMethod;
import org.sure_vote.entity.ElectionStatus;
import org.sure_vote.entity.ElectionVotingPrecinctAssignment;
import org.sure_vote.entity.HeldKey;
import org.sure_vote.entity.HeldKeyPurpose;
import org.sure_vote.entity.Locality;
import org.sure_vote.entity.Outcome;
import org.sure_vote.entity.Person;
import org.sure_vote.entity.Race;
import org.sure_vote.entity.RaceCandidate;
import org.sure_vote.entity.RaceType;
import org.sure_vote.entity.RecordingDevice;
import org.sure_vote.entity.RecordingDeviceKind;
import org.sure_vote.entity.RecordingPlace;
import org.sure_vote.entity.RecordingPlaceKind;
import org.sure_vote.entity.Seat;
import org.sure_vote.entity.SeatJurisdiction;
import org.sure_vote.entity.SeatType;
import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.TenantKind;
import org.sure_vote.entity.TenantStatus;
import org.sure_vote.entity.Term;
import org.sure_vote.entity.VotingPrecinct;
import org.sure_vote.repository.InMemorySureVoteRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShowcaseRulesTest {
    @Test
    void aShowcaseBallotReturnsTheCodeOnceAndCloses() {
        final var world = this.world();
        final var cast = world.cast();
        final var found = world.app.lookup(world.electionId, cast.code(), null);
        final var castResult = cast.result();
        final var castBallot = castResult.ballot();
        final var foundBallot = found.ballot();
        final var payload = castBallot.getPayload();
        final var keys = world.repository.list(HeldKey.class);
        final var operator = this.operator(keys, world.electionId);
        final var message = Digests.sha256Hex("showcase");
        final var hashRaw = Digests.unhex(message);
        final var seed = Digests.unhex(operator.getSeed());
        final var signature = Signatures.sign(seed, hashRaw);
        final var stored = world.repository.find(Election.class, world.electionId);
        final var election = stored.orElseThrow();
        final var closed = world.app.closeShowcase(world.electionId, world.actor);
        final var slots = world.repository.list(RaceCandidate.class);
        final var winner = this.winner(slots, world.slotId);
        assertEquals(castBallot.getId(), foundBallot.getId());
        assertFalse(payload.contains(cast.code()));
        assertEquals(4, keys.size());
        assertTrue(Signatures.verify(election.getOperatorPublicKey(), hashRaw, signature));
        assertTrue(world.app.demonstration(world.electionId));
        assertEquals(ElectionStatus.HISTORICAL, closed.getStatus());
        assertEquals(Outcome.WON, winner.getOutcome());
        assertEquals(Integer.valueOf(1), winner.getRank());
        assertEquals(1, winner.getVotesReceived());
        assertTrue(world.ready);
    }

    @Test
    void aProductionTenantCannotKeepHostKeys() {
        final var world = SampleElection.upcomingBare();
        final var failure = this.rejectedShowcase(world);
        final var message = failure.getMessage();
        assertEquals("only a showcase tenant keeps host keys", message);
    }

    private ConstraintException rejectedShowcase(final SampleElection world) {
        final var now = Instant.now();
        final var start = now.minus(Duration.ofHours(1));
        final var end = now.plus(Duration.ofDays(1));
        final var draft = this.rejectedDraft(world, start, end);
        return assertThrows(
            ConstraintException.class,
            () -> world.app.createShowcaseElection(draft, world.actor)
        );
    }

    private Election rejectedDraft(final SampleElection world, final Instant start, final Instant end) {
        final var draft = new Election();
        draft.setTenantId(world.tenantId);
        draft.setLocalityId(world.localityId);
        draft.setName("Nope");
        draft.setVotingStart(start);
        draft.setVotingEnd(end);
        return draft;
    }

    private Fixture world() {
        final var repository = new InMemorySureVoteRepository();
        final var app = new SureVote(repository);
        final var actor = UUID.randomUUID();
        final var now = Instant.now();
        final var tenant = this.tenant(app, actor);
        final var tenantId = tenant.getId();
        final var locality = this.locality(app, actor, tenantId);
        final var address = this.address(app, actor, tenantId, locality.getId());
        final var precinct = this.precinct(app, actor, tenantId, address.getId());
        final var seat = this.seat(app, actor, tenantId, locality.getId());
        final var term = this.term(app, actor, tenantId, seat.getId());
        final var election = this.election(app, actor, tenantId, locality.getId(), now);
        final var race = this.race(app, actor, tenantId, election.getId(), term.getId());
        final var person = this.person(app, actor, tenantId, "Ada", "One");
        final var voter = this.person(app, actor, tenantId, "Cam", "Voter");
        final var candidate = this.candidate(app, actor, tenantId, person.getId());
        final var slot = this.slot(app, actor, tenantId, race.getId(), candidate.getId());
        final var place = this.place(app, actor, tenantId, locality.getId());
        final var device = this.device(app, actor, tenantId);
        final var style = this.style(app, actor, tenantId, election.getId(), precinct.getId(), race.getId());
        final var assignment = this.assignment(app, actor, tenantId, election.getId(), precinct.getId());
        final var styleElection = style.getElectionId();
        final var assignmentElection = assignment.getElectionId();
        final var ready = styleElection.equals(assignmentElection);
        final var active = app.activateShowcase(election.getId(), actor);
        return new Fixture(
            repository,
            app,
            actor,
            active.getId(),
            precinct.getId(),
            device.getId(),
            place.getId(),
            voter.getId(),
            slot.getId(),
            ready
        );
    }

    private Tenant tenant(final SureVote app, final UUID actor) {
        final var tenant = new Tenant();
        tenant.setName("Town");
        tenant.setKind(TenantKind.SHOWCASE);
        tenant.setResidencyRegion("us-east");
        tenant.setStatus(TenantStatus.ACTIVE);
        return app.createTenant(tenant, actor);
    }

    private Locality locality(final SureVote app, final UUID actor, final UUID tenantId) {
        final var locality = new Locality();
        locality.setTenantId(tenantId);
        locality.setName("Town");
        locality.setType("city");
        return app.createLocality(locality, actor);
    }

    private Address address(final SureVote app, final UUID actor, final UUID tenantId, final UUID localityId) {
        final var address = new Address();
        address.setTenantId(tenantId);
        address.setLocalityId(localityId);
        address.setStreet1("1 Main");
        address.setPostalCode("00000");
        return app.createAddress(address, actor);
    }

    private VotingPrecinct precinct(
        final SureVote app,
        final UUID actor,
        final UUID tenantId,
        final UUID addressId
    ) {
        final var precinct = new VotingPrecinct();
        precinct.setTenantId(tenantId);
        precinct.setName("Precinct 1");
        precinct.setAddressId(addressId);
        return app.createPrecinct(precinct, actor);
    }

    private Seat seat(final SureVote app, final UUID actor, final UUID tenantId, final UUID localityId) {
        final var seat = new Seat();
        seat.setTenantId(tenantId);
        seat.setLocalityId(localityId);
        seat.setName("Mayor");
        seat.setJurisdiction(SeatJurisdiction.LOCAL);
        seat.setType(SeatType.MAYOR);
        seat.setAppointments(1);
        seat.setTermLength(4);
        return app.createSeat(seat, actor);
    }

    private Term term(final SureVote app, final UUID actor, final UUID tenantId, final UUID seatId) {
        final var term = new Term();
        term.setTenantId(tenantId);
        term.setSeatId(seatId);
        return app.createTerm(term, actor);
    }

    private Election election(
        final SureVote app,
        final UUID actor,
        final UUID tenantId,
        final UUID localityId,
        final Instant now
    ) {
        final var election = new Election();
        final var start = now.minus(Duration.ofHours(1));
        final var end = now.plus(Duration.ofDays(1));
        election.setTenantId(tenantId);
        election.setLocalityId(localityId);
        election.setName("General");
        election.setVotingStart(start);
        election.setVotingEnd(end);
        return app.createShowcaseElection(election, actor);
    }

    private Race race(
        final SureVote app,
        final UUID actor,
        final UUID tenantId,
        final UUID electionId,
        final UUID termId
    ) {
        final var race = new Race();
        race.setTenantId(tenantId);
        race.setElectionId(electionId);
        race.setTermId(termId);
        race.setName("Mayor");
        race.setType(RaceType.GENERAL);
        race.setMethod(ElectionMethod.FIRST_PAST_THE_POST);
        return app.createRace(race, actor);
    }

    private Person person(
        final SureVote app,
        final UUID actor,
        final UUID tenantId,
        final String first,
        final String last
    ) {
        final var person = new Person();
        person.setTenantId(tenantId);
        person.setFirstName(first);
        person.setLastName(last);
        return app.createPerson(person, actor);
    }

    private Candidate candidate(final SureVote app, final UUID actor, final UUID tenantId, final UUID personId) {
        final var candidate = new Candidate();
        candidate.setTenantId(tenantId);
        candidate.setPersonId(personId);
        return app.createCandidate(candidate, actor);
    }

    private RaceCandidate slot(
        final SureVote app,
        final UUID actor,
        final UUID tenantId,
        final UUID raceId,
        final UUID candidateId
    ) {
        final var slot = new RaceCandidate();
        slot.setTenantId(tenantId);
        slot.setRaceId(raceId);
        slot.setCandidateId(candidateId);
        return app.createRaceCandidate(slot, actor);
    }

    private BallotStyle style(
        final SureVote app,
        final UUID actor,
        final UUID tenantId,
        final UUID electionId,
        final UUID precinctId,
        final UUID raceId
    ) {
        final var style = new BallotStyle();
        style.setTenantId(tenantId);
        style.setElectionId(electionId);
        style.setVotingPrecinctId(precinctId);
        style.setRaceId(raceId);
        return app.createBallotStyle(style, actor);
    }

    private RecordingPlace place(final SureVote app, final UUID actor, final UUID tenantId, final UUID localityId) {
        final var place = new RecordingPlace();
        place.setTenantId(tenantId);
        place.setLocalityId(localityId);
        place.setName("Town Hall");
        place.setKind(RecordingPlaceKind.POLLING_PLACE);
        return app.createRecordingPlace(place, actor);
    }

    private RecordingDevice device(final SureVote app, final UUID actor, final UUID tenantId) {
        final var device = new RecordingDevice();
        device.setTenantId(tenantId);
        device.setName("Machine 1");
        device.setKind(RecordingDeviceKind.POLLING_PLACE_MACHINE);
        return app.createShowcaseDevice(device, actor);
    }

    private ElectionVotingPrecinctAssignment assignment(
        final SureVote app,
        final UUID actor,
        final UUID tenantId,
        final UUID electionId,
        final UUID precinctId
    ) {
        final var assignment = new ElectionVotingPrecinctAssignment();
        assignment.setTenantId(tenantId);
        assignment.setElectionId(electionId);
        assignment.setVotingPrecinctId(precinctId);
        assignment.setAssignmentStatus(AssignmentStatus.CURRENT);
        return app.assignPrecinct(assignment, actor);
    }

    private HeldKey operator(final List<HeldKey> keys, final UUID electionId) {
        for (final var key : keys) {
            final var matched = this.matchOperator(key, electionId);
            if (matched != null) {
                return matched;
            }
        }
        throw new IllegalStateException("missing operator key");
    }

    private HeldKey matchOperator(final HeldKey key, final UUID electionId) {
        final var operator = key.getPurpose() == HeldKeyPurpose.OPERATOR;
        final var same = electionId.equals(key.getSubjectId());
        if (operator && same) {
            return key;
        }
        else {
            return null;
        }
    }

    private RaceCandidate winner(final List<RaceCandidate> slots, final UUID slotId) {
        for (final var slot : slots) {
            final var matched = this.matchSlot(slot, slotId);
            if (matched != null) {
                return matched;
            }
        }
        throw new IllegalStateException("missing slot");
    }

    private RaceCandidate matchSlot(final RaceCandidate slot, final UUID slotId) {
        final var id = slot.getId();
        if (slotId.equals(id)) {
            return slot;
        }
        else {
            return null;
        }
    }

    private static final class Fixture {
        private final InMemorySureVoteRepository repository;
        private final SureVote app;
        private final UUID actor;
        private final UUID electionId;
        private final UUID precinctId;
        private final UUID deviceId;
        private final UUID placeId;
        private final UUID personId;
        private final UUID slotId;
        private final boolean ready;

        private Fixture(
            final InMemorySureVoteRepository repository,
            final SureVote app,
            final UUID actor,
            final UUID electionId,
            final UUID precinctId,
            final UUID deviceId,
            final UUID placeId,
            final UUID personId,
            final UUID slotId,
            final boolean ready
        ) {
            this.repository = repository;
            this.app = app;
            this.actor = actor;
            this.electionId = electionId;
            this.precinctId = precinctId;
            this.deviceId = deviceId;
            this.placeId = placeId;
            this.personId = personId;
            this.slotId = slotId;
            this.ready = ready;
        }

        private DemoCast cast() {
            final var selection = new DemoSelection(this.slotId, "Y");
            final var selections = List.of(selection);
            return this.app.castShowcase(
                this.electionId,
                this.precinctId,
                this.deviceId,
                this.placeId,
                this.personId,
                selections,
                this.actor
            );
        }
    }
}
