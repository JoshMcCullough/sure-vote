package org.sure_vote.api;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;

import org.sure_vote.entity.Address;
import org.sure_vote.entity.AssignmentStatus;
import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.BallotStyle;
import org.sure_vote.entity.Candidate;
import org.sure_vote.entity.DistrictType;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.ElectionMethod;
import org.sure_vote.entity.ElectionVotingPrecinctAssignment;
import org.sure_vote.entity.Locality;
import org.sure_vote.entity.Person;
import org.sure_vote.entity.PrecinctDistrict;
import org.sure_vote.entity.Race;
import org.sure_vote.entity.RaceCandidate;
import org.sure_vote.entity.RaceType;
import org.sure_vote.entity.RecordingDevice;
import org.sure_vote.entity.RecordingDeviceKind;
import org.sure_vote.entity.RecordingDeviceStatus;
import org.sure_vote.entity.RecordingPlace;
import org.sure_vote.entity.RecordingPlaceKind;
import org.sure_vote.entity.ReferendumChoice;
import org.sure_vote.entity.Seat;
import org.sure_vote.entity.SeatJurisdiction;
import org.sure_vote.entity.SeatType;
import org.sure_vote.entity.Term;
import org.sure_vote.entity.Vote;
import org.sure_vote.entity.VotingDistrict;
import org.sure_vote.entity.VotingPrecinct;
import org.sure_vote.entity.WireValue;
import org.sure_vote.entity.Wires;
import org.sure_vote.resource.AddressResource;
import org.sure_vote.resource.AssignmentResource;
import org.sure_vote.resource.BallotResource;
import org.sure_vote.resource.BallotStyleResource;
import org.sure_vote.resource.CandidateResource;
import org.sure_vote.resource.DemoCastResource;
import org.sure_vote.resource.DeviceResource;
import org.sure_vote.resource.DistrictResource;
import org.sure_vote.resource.ElectionResource;
import org.sure_vote.resource.ErrorResource;
import org.sure_vote.resource.LocalityResource;
import org.sure_vote.resource.PersonResource;
import org.sure_vote.resource.PlaceResource;
import org.sure_vote.resource.PrecinctDistrictResource;
import org.sure_vote.resource.PrecinctResource;
import org.sure_vote.resource.RaceCandidateResource;
import org.sure_vote.resource.RaceResource;
import org.sure_vote.resource.SeatResource;
import org.sure_vote.resource.TermResource;
import org.sure_vote.resource.VoteResource;
import org.sure_vote.resource.WorkspaceResource;
import org.sure_vote.service.CanonicalJson;
import org.sure_vote.service.ConstraintException;
import org.sure_vote.service.DemoCast;
import org.sure_vote.service.DemoSelection;
import org.sure_vote.service.LookupResult;
import org.sure_vote.service.RaceEdit;
import org.sure_vote.service.SureVote;
import org.sure_vote.service.WorkspaceView;

final class WorkspaceHttp {
    private static final Set<String> COLLECTIONS = Set.of(
        "localities",
        "addresses",
        "precincts",
        "districts",
        "precinct-districts",
        "seats",
        "terms",
        "races",
        "candidates",
        "race-candidates",
        "ballot-styles",
        "places",
        "devices",
        "precinct-assignments"
    );

    private final SureVote app;
    private final ObjectMapper json;
    private final Requests requests;

    WorkspaceHttp(final SureVote app, final ObjectMapper json, final Requests requests) {
        this.app = app;
        this.json = json;
        this.requests = requests;
    }

    boolean handles(final String method, final List<String> parts) {
        final var collection = this.collection(method, parts);
        final var workspace = this.tenantWorkspace(method, parts);
        final var showcase = this.showcase(method, parts);
        return collection || workspace || showcase || this.raceEdit(method, parts);
    }

    RouteGroup group(final String method, final List<String> parts) {
        if (this.showcase(method, parts)) {
            return RouteGroup.SHOWCASE;
        }
        else {
            return RouteGroup.OFFICIAL;
        }
    }

    void handle(final HttpExchange exchange, final String method, final List<String> parts) throws IOException {
        if (this.collection(method, parts)) {
            this.create(exchange, parts.getFirst());
        }
        else if (this.tenantWorkspace(method, parts)) {
            this.workspace(exchange);
        }
        else if (this.showcase(method, parts)) {
            this.showcase(exchange, parts);
        }
        else if (this.raceEdit(method, parts)) {
            this.updateRace(exchange, parts.get(1));
        }
        else {
            this.writeJson(exchange, 404, new ErrorResource("not found"));
        }
    }

    private boolean collection(final String method, final List<String> parts) {
        final var post = "POST".equals(method);
        final var one = parts.size() == 1;
        if (post && one) {
            return COLLECTIONS.contains(parts.getFirst());
        }
        else {
            return false;
        }
    }

    private boolean raceEdit(final String method, final List<String> parts) {
        final var patch = "PATCH".equals(method);
        return patch && parts.size() == 2 && "races".equals(parts.getFirst());
    }

    private void updateRace(final HttpExchange exchange, final String id) throws IOException {
        final var actor = this.requests.official(exchange);
        final var body = this.read(exchange, RaceEditBody.class);
        final var method = this.optionalMethod(body.method);
        final var edit = new RaceEdit(UUID.fromString(id), body.name, method, body.previousRaceId);
        final var raceId = edit.raceId();
        final var saved = this.app.asTenantRow(actor, Race.class, raceId, () -> this.app.updateRace(edit, actor.id()));
        this.writeJson(exchange, 200, this.raceResource(saved));
    }

    private ElectionMethod optionalMethod(final String value) {
        if (value == null) {
            return null;
        }
        else {
            return this.wire(ElectionMethod.values(), value, "election method");
        }
    }

    private boolean tenantWorkspace(final String method, final List<String> parts) {
        final var get = "GET".equals(method);
        final var one = parts.size() == 1;
        return get && one && "workspace".equals(parts.getFirst());
    }

    private boolean showcase(final String method, final List<String> parts) {
        final var post = "POST".equals(method);
        final var root = this.first(parts);
        if (post && "showcase".equals(root)) {
            return this.showcaseShape(parts);
        }
        else {
            return false;
        }
    }

    private String first(final List<String> parts) {
        if (parts.isEmpty()) {
            return "";
        }
        else {
            return parts.getFirst();
        }
    }

    private boolean showcaseShape(final List<String> parts) {
        final var size = parts.size();
        if (size == 2) {
            return this.showcaseCreate(parts.get(1));
        }
        else if (size == 4) {
            return this.showcaseAction(parts);
        }
        else {
            return false;
        }
    }

    private boolean showcaseCreate(final String name) {
        return "elections".equals(name) || "devices".equals(name);
    }

    private boolean showcaseAction(final List<String> parts) {
        final var kind = parts.get(1);
        final var action = parts.get(3);
        final var election = "elections".equals(kind);
        final var known = "activate".equals(action) || "cast".equals(action) || "close".equals(action);
        return election && known;
    }

    private void create(final HttpExchange exchange, final String name) throws IOException {
        if ("localities".equals(name)) {
            this.createLocality(exchange);
        }
        else if ("addresses".equals(name)) {
            this.createAddress(exchange);
        }
        else if ("precincts".equals(name)) {
            this.createPrecinct(exchange);
        }
        else if ("districts".equals(name)) {
            this.createDistrict(exchange);
        }
        else if ("precinct-districts".equals(name)) {
            this.createPrecinctDistrict(exchange);
        }
        else if ("seats".equals(name)) {
            this.createSeat(exchange);
        }
        else if ("terms".equals(name)) {
            this.createTerm(exchange);
        }
        else if ("races".equals(name)) {
            this.createRace(exchange);
        }
        else if ("candidates".equals(name)) {
            this.createCandidate(exchange);
        }
        else if ("race-candidates".equals(name)) {
            this.createRaceCandidate(exchange);
        }
        else if ("ballot-styles".equals(name)) {
            this.createBallotStyle(exchange);
        }
        else if ("places".equals(name)) {
            this.createPlace(exchange);
        }
        else if ("devices".equals(name)) {
            this.createDevice(exchange);
        }
        else if ("precinct-assignments".equals(name)) {
            this.createAssignment(exchange);
        }
        else {
            this.writeJson(exchange, 404, new ErrorResource("not found"));
        }
    }

    private void showcase(final HttpExchange exchange, final List<String> parts) throws IOException {
        final var size = parts.size();
        if (size == 2) {
            this.showcaseCreate(exchange, parts.get(1));
        }
        else {
            this.showcaseAction(exchange, parts);
        }
    }

    private void showcaseCreate(final HttpExchange exchange, final String name) throws IOException {
        if ("elections".equals(name)) {
            this.createShowcaseElection(exchange);
        }
        else {
            this.createShowcaseDevice(exchange);
        }
    }

    private void showcaseAction(final HttpExchange exchange, final List<String> parts) throws IOException {
        final var id = this.id(parts.get(2));
        final var action = parts.get(3);
        if ("activate".equals(action)) {
            this.activateShowcase(exchange, id);
        }
        else if ("cast".equals(action)) {
            this.castShowcase(exchange, id);
        }
        else {
            this.closeShowcase(exchange, id);
        }
    }

    private void workspace(final HttpExchange exchange) throws IOException {
        final var actor = this.requests.member(exchange);
        final var view = this.app.asTenant(actor, () -> this.app.workspace(actor.tenantId(), actor.id()));
        this.writeJson(exchange, 200, this.workspaceResource(view));
    }

    private void createLocality(final HttpExchange exchange) throws IOException {
        final var actor = this.requests.member(exchange);
        final var body = this.read(exchange, LocalityBody.class);
        final var locality = this.locality(body, actor.tenantId());
        final var saved = this.app.asTenant(actor, () -> this.app.createLocality(locality, actor.id()));
        this.writeJson(exchange, 201, this.localityResource(saved));
    }

    private void createAddress(final HttpExchange exchange) throws IOException {
        final var actor = this.requests.member(exchange);
        final var body = this.read(exchange, AddressBody.class);
        final var address = this.address(body, actor.tenantId());
        final var saved = this.app.asTenant(actor, () -> this.app.createAddress(address, actor.id()));
        this.writeJson(exchange, 201, this.addressResource(saved));
    }

    private void createPrecinct(final HttpExchange exchange) throws IOException {
        final var actor = this.requests.member(exchange);
        final var body = this.read(exchange, PrecinctBody.class);
        final var precinct = this.precinct(body, actor.tenantId());
        final var saved = this.app.asTenant(actor, () -> this.app.createPrecinct(precinct, actor.id()));
        this.writeJson(exchange, 201, this.precinctResource(saved));
    }

    private void createDistrict(final HttpExchange exchange) throws IOException {
        final var actor = this.requests.member(exchange);
        final var body = this.read(exchange, DistrictBody.class);
        final var district = this.district(body, actor.tenantId());
        final var saved = this.app.asTenant(actor, () -> this.app.createVotingDistrict(district, actor.id()));
        this.writeJson(exchange, 201, this.districtResource(saved));
    }

    private void createPrecinctDistrict(final HttpExchange exchange) throws IOException {
        final var actor = this.requests.member(exchange);
        final var body = this.read(exchange, PrecinctDistrictBody.class);
        final var row = this.precinctDistrict(body, actor.tenantId());
        final var saved = this.app.asTenant(actor, () -> this.app.createPrecinctDistrict(row, actor.id()));
        this.writeJson(exchange, 201, this.precinctDistrictResource(saved));
    }

    private void createSeat(final HttpExchange exchange) throws IOException {
        final var actor = this.requests.member(exchange);
        final var body = this.read(exchange, SeatBody.class);
        final var seat = this.seat(body, actor.tenantId());
        final var saved = this.app.asTenant(actor, () -> this.app.createSeat(seat, actor.id()));
        this.writeJson(exchange, 201, this.seatResource(saved));
    }

    private void createTerm(final HttpExchange exchange) throws IOException {
        final var actor = this.requests.member(exchange);
        final var body = this.read(exchange, TermBody.class);
        final var term = this.term(body, actor.tenantId());
        final var saved = this.app.asTenant(actor, () -> this.app.createTerm(term, actor.id()));
        this.writeJson(exchange, 201, this.termResource(saved));
    }

    private void createRace(final HttpExchange exchange) throws IOException {
        final var actor = this.requests.member(exchange);
        final var body = this.read(exchange, RaceBody.class);
        final var race = this.race(body, actor.tenantId());
        final var saved = this.app.asTenant(actor, () -> this.app.createRace(race, actor.id()));
        this.writeJson(exchange, 201, this.raceResource(saved));
    }

    private void createCandidate(final HttpExchange exchange) throws IOException {
        final var actor = this.requests.member(exchange);
        final var body = this.read(exchange, CandidateBody.class);
        final var candidate = this.candidate(body, actor.tenantId());
        final var saved = this.app.asTenant(actor, () -> this.app.createCandidate(candidate, actor.id()));
        this.writeJson(exchange, 201, this.candidateResource(saved));
    }

    private void createRaceCandidate(final HttpExchange exchange) throws IOException {
        final var actor = this.requests.member(exchange);
        final var body = this.read(exchange, RaceCandidateBody.class);
        final var slot = this.raceCandidate(body, actor.tenantId());
        final var saved = this.app.asTenant(actor, () -> this.app.createRaceCandidate(slot, actor.id()));
        this.writeJson(exchange, 201, this.raceCandidateResource(saved));
    }

    private void createBallotStyle(final HttpExchange exchange) throws IOException {
        final var actor = this.requests.member(exchange);
        final var body = this.read(exchange, BallotStyleBody.class);
        final var style = this.ballotStyle(body, actor.tenantId());
        final var saved = this.app.asTenant(actor, () -> this.app.createBallotStyle(style, actor.id()));
        this.writeJson(exchange, 201, this.ballotStyleResource(saved));
    }

    private void createPlace(final HttpExchange exchange) throws IOException {
        final var actor = this.requests.member(exchange);
        final var body = this.read(exchange, PlaceBody.class);
        final var place = this.place(body, actor.tenantId());
        final var saved = this.app.asTenant(actor, () -> this.app.createRecordingPlace(place, actor.id()));
        this.writeJson(exchange, 201, this.placeResource(saved));
    }

    private void createDevice(final HttpExchange exchange) throws IOException {
        final var actor = this.requests.member(exchange);
        final var body = this.read(exchange, DeviceBody.class);
        final var device = this.device(body, actor.tenantId());
        final var saved = this.app.asTenant(actor, () -> this.app.createRecordingDevice(device, actor.id()));
        this.writeJson(exchange, 201, this.deviceResource(saved));
    }

    private void createAssignment(final HttpExchange exchange) throws IOException {
        final var actor = this.requests.member(exchange);
        final var body = this.read(exchange, AssignmentBody.class);
        final var assignment = this.assignment(body, actor.tenantId());
        final var saved = this.app.asTenant(actor, () -> this.app.assignPrecinct(assignment, actor.id()));
        this.writeJson(exchange, 201, this.assignmentResource(saved));
    }

    private void createShowcaseElection(final HttpExchange exchange) throws IOException {
        final var actor = this.requests.member(exchange);
        final var body = this.read(exchange, ShowcaseElectionBody.class);
        final var election = this.showcaseElection(body, actor.tenantId());
        final var saved = this.app.asTenant(actor, () -> this.app.createShowcaseElection(election, actor.id()));
        this.writeJson(exchange, 201, this.electionResource(saved));
    }

    private void createShowcaseDevice(final HttpExchange exchange) throws IOException {
        final var actor = this.requests.member(exchange);
        final var body = this.read(exchange, ShowcaseDeviceBody.class);
        final var device = this.showcaseDevice(body, actor.tenantId());
        final var saved = this.app.asTenant(actor, () -> this.app.createShowcaseDevice(device, actor.id()));
        this.writeJson(exchange, 201, this.deviceResource(saved));
    }

    private void activateShowcase(final HttpExchange exchange, final UUID id) throws IOException {
        final var actor = this.requests.member(exchange);
        final var saved = this.app.inElection(actor, id, () -> this.app.activateShowcase(id, actor.id()));
        this.writeJson(exchange, 200, this.electionResource(saved));
    }

    private void castShowcase(final HttpExchange exchange, final UUID id) throws IOException {
        final var actor = this.requests.member(exchange);
        final var body = this.read(exchange, CastBody.class);
        final var selections = this.selections(body.selections);
        final var cast = this.app.inElection(actor, id, () -> this.app.castShowcase(
            id,
            body.votingPrecinctId,
            body.recordingDeviceId,
            body.recordingPlaceId,
            body.personId,
            selections,
            actor.id()
        ));
        this.writeJson(exchange, 200, this.demoCastResource(cast));
    }

    private void closeShowcase(final HttpExchange exchange, final UUID id) throws IOException {
        final var actor = this.requests.member(exchange);
        final var saved = this.app.inElection(actor, id, () -> this.app.closeShowcase(id, actor.id()));
        this.writeJson(exchange, 200, this.electionResource(saved));
    }

    private Locality locality(final LocalityBody body, final UUID tenantId) {
        final var locality = new Locality();
        locality.setTenantId(tenantId);
        locality.setName(body.name);
        locality.setType(body.type);
        locality.setParentId(body.parentId);
        return locality;
    }

    private Address address(final AddressBody body, final UUID tenantId) {
        final var address = new Address();
        address.setTenantId(tenantId);
        address.setLocalityId(body.localityId);
        address.setStreet1(body.street1);
        address.setPostalCode(body.postalCode);
        return address;
    }

    private VotingPrecinct precinct(final PrecinctBody body, final UUID tenantId) {
        final var precinct = new VotingPrecinct();
        precinct.setTenantId(tenantId);
        precinct.setName(body.name);
        precinct.setAddressId(body.addressId);
        return precinct;
    }

    private VotingDistrict district(final DistrictBody body, final UUID tenantId) {
        final var district = new VotingDistrict();
        district.setTenantId(tenantId);
        district.setLocalityId(body.localityId);
        district.setName(body.name);
        district.setType(this.wire(DistrictType.values(), body.type, "district type"));
        return district;
    }

    private PrecinctDistrict precinctDistrict(final PrecinctDistrictBody body, final UUID tenantId) {
        final var row = new PrecinctDistrict();
        row.setTenantId(tenantId);
        row.setVotingPrecinctId(body.votingPrecinctId);
        row.setVotingDistrictId(body.votingDistrictId);
        row.setDistrictType(this.wire(DistrictType.values(), body.districtType, "district type"));
        return row;
    }

    private Seat seat(final SeatBody body, final UUID tenantId) {
        final var seat = new Seat();
        seat.setTenantId(tenantId);
        seat.setLocalityId(body.localityId);
        seat.setName(body.name);
        seat.setJurisdiction(this.wire(SeatJurisdiction.values(), body.jurisdiction, "seat jurisdiction"));
        seat.setType(this.wire(SeatType.values(), body.type, "seat type"));
        seat.setAppointments(body.appointments);
        seat.setTermLength(body.termLength);
        return seat;
    }

    private Term term(final TermBody body, final UUID tenantId) {
        final var term = new Term();
        term.setTenantId(tenantId);
        term.setSeatId(body.seatId);
        return term;
    }

    private Election showcaseElection(final ShowcaseElectionBody body, final UUID tenantId) {
        final var election = new Election();
        election.setTenantId(tenantId);
        election.setLocalityId(body.localityId);
        election.setName(body.name);
        election.setVotingStart(this.instant(body.votingStart));
        election.setVotingEnd(this.instant(body.votingEnd));
        return election;
    }

    private Race race(final RaceBody body, final UUID tenantId) {
        final var race = new Race();
        race.setTenantId(tenantId);
        race.setElectionId(body.electionId);
        race.setTermId(body.termId);
        race.setName(body.name);
        race.setType(this.wire(RaceType.values(), body.type, "race type"));
        race.setMethod(this.wire(ElectionMethod.values(), body.method, "election method"));
        race.setPreviousRaceId(body.previousRaceId);
        return race;
    }

    private Candidate candidate(final CandidateBody body, final UUID tenantId) {
        final var candidate = new Candidate();
        candidate.setTenantId(tenantId);
        candidate.setPersonId(body.personId);
        candidate.setPartyAffiliation(body.partyAffiliation);
        return candidate;
    }

    private RaceCandidate raceCandidate(final RaceCandidateBody body, final UUID tenantId) {
        final var slot = new RaceCandidate();
        slot.setTenantId(tenantId);
        slot.setRaceId(body.raceId);
        slot.setCandidateId(body.candidateId);
        slot.setWriteIn(body.writeIn);
        slot.setReferendumChoice(this.choice(body.referendumChoice));
        return slot;
    }

    private BallotStyle ballotStyle(final BallotStyleBody body, final UUID tenantId) {
        final var style = new BallotStyle();
        style.setTenantId(tenantId);
        style.setElectionId(body.electionId);
        style.setVotingPrecinctId(body.votingPrecinctId);
        style.setRaceId(body.raceId);
        return style;
    }

    private RecordingPlace place(final PlaceBody body, final UUID tenantId) {
        final var place = new RecordingPlace();
        place.setTenantId(tenantId);
        place.setLocalityId(body.localityId);
        place.setName(body.name);
        place.setKind(this.wire(RecordingPlaceKind.values(), body.kind, "recording place kind"));
        place.setAddressId(body.addressId);
        return place;
    }

    private RecordingDevice device(final DeviceBody body, final UUID tenantId) {
        final var device = new RecordingDevice();
        device.setTenantId(tenantId);
        device.setName(body.name);
        device.setKind(this.wire(RecordingDeviceKind.values(), body.kind, "recording device kind"));
        device.setPublicKey(body.publicKey);
        device.setStatus(this.wire(RecordingDeviceStatus.values(), body.status, "recording device status"));
        return device;
    }

    private RecordingDevice showcaseDevice(final ShowcaseDeviceBody body, final UUID tenantId) {
        final var device = new RecordingDevice();
        device.setTenantId(tenantId);
        device.setName(body.name);
        device.setKind(this.wire(RecordingDeviceKind.values(), body.kind, "recording device kind"));
        return device;
    }

    private ElectionVotingPrecinctAssignment assignment(final AssignmentBody body, final UUID tenantId) {
        final var assignment = new ElectionVotingPrecinctAssignment();
        assignment.setTenantId(tenantId);
        assignment.setElectionId(body.electionId);
        assignment.setVotingPrecinctId(body.votingPrecinctId);
        assignment.setAssignmentStatus(this.assignmentStatus(body.assignmentStatus));
        return assignment;
    }

    private List<DemoSelection> selections(final List<SelectionBody> bodies) {
        final var selections = new ArrayList<DemoSelection>();
        this.addSelections(selections, bodies);
        return selections;
    }

    private void addSelections(final List<DemoSelection> selections, final List<SelectionBody> bodies) {
        if (bodies == null) {
            return;
        }
        else {
            for (final var body : bodies) {
                selections.add(new DemoSelection(body.raceCandidateId, body.value));
            }
        }
    }

    private WorkspaceResource workspaceResource(final WorkspaceView view) {
        final var resource = new WorkspaceResource();
        resource.setLocalities(this.localities(view.getLocalities()));
        resource.setAddresses(this.addresses(view.getAddresses()));
        resource.setPrecincts(this.precincts(view.getPrecincts()));
        resource.setDistricts(this.districts(view.getDistricts()));
        resource.setPrecinctDistricts(this.precinctDistricts(view.getPrecinctDistricts()));
        resource.setSeats(this.seats(view.getSeats()));
        resource.setTerms(this.terms(view.getTerms()));
        resource.setElections(this.elections(view.getElections()));
        resource.setRaces(this.races(view.getRaces()));
        resource.setPersons(this.persons(view.getPersons()));
        resource.setCandidates(this.candidates(view.getCandidates()));
        resource.setRaceCandidates(this.raceCandidates(view.getRaceCandidates()));
        resource.setBallotStyles(this.ballotStyles(view.getBallotStyles()));
        resource.setPlaces(this.places(view.getPlaces()));
        resource.setDevices(this.devices(view.getDevices()));
        resource.setAssignments(this.assignments(view.getAssignments()));
        return resource;
    }

    private List<LocalityResource> localities(final List<Locality> rows) {
        final var resources = new ArrayList<LocalityResource>();
        this.addLocalities(resources, rows);
        return resources;
    }

    private void addLocalities(final List<LocalityResource> resources, final List<Locality> rows) {
        for (final var row : rows) {
            resources.add(this.localityResource(row));
        }
    }

    private List<AddressResource> addresses(final List<Address> rows) {
        final var resources = new ArrayList<AddressResource>();
        this.addAddresses(resources, rows);
        return resources;
    }

    private void addAddresses(final List<AddressResource> resources, final List<Address> rows) {
        for (final var row : rows) {
            resources.add(this.addressResource(row));
        }
    }

    private List<PrecinctResource> precincts(final List<VotingPrecinct> rows) {
        final var resources = new ArrayList<PrecinctResource>();
        this.addPrecincts(resources, rows);
        return resources;
    }

    private void addPrecincts(final List<PrecinctResource> resources, final List<VotingPrecinct> rows) {
        for (final var row : rows) {
            resources.add(this.precinctResource(row));
        }
    }

    private List<DistrictResource> districts(final List<VotingDistrict> rows) {
        final var resources = new ArrayList<DistrictResource>();
        this.addDistricts(resources, rows);
        return resources;
    }

    private void addDistricts(final List<DistrictResource> resources, final List<VotingDistrict> rows) {
        for (final var row : rows) {
            resources.add(this.districtResource(row));
        }
    }

    private List<PrecinctDistrictResource> precinctDistricts(final List<PrecinctDistrict> rows) {
        final var resources = new ArrayList<PrecinctDistrictResource>();
        this.addPrecinctDistricts(resources, rows);
        return resources;
    }

    private void addPrecinctDistricts(
        final List<PrecinctDistrictResource> resources,
        final List<PrecinctDistrict> rows
    ) {
        for (final var row : rows) {
            resources.add(this.precinctDistrictResource(row));
        }
    }

    private List<SeatResource> seats(final List<Seat> rows) {
        final var resources = new ArrayList<SeatResource>();
        this.addSeats(resources, rows);
        return resources;
    }

    private void addSeats(final List<SeatResource> resources, final List<Seat> rows) {
        for (final var row : rows) {
            resources.add(this.seatResource(row));
        }
    }

    private List<TermResource> terms(final List<Term> rows) {
        final var resources = new ArrayList<TermResource>();
        this.addTerms(resources, rows);
        return resources;
    }

    private void addTerms(final List<TermResource> resources, final List<Term> rows) {
        for (final var row : rows) {
            resources.add(this.termResource(row));
        }
    }

    private List<ElectionResource> elections(final List<Election> rows) {
        final var resources = new ArrayList<ElectionResource>();
        this.addElections(resources, rows);
        return resources;
    }

    private void addElections(final List<ElectionResource> resources, final List<Election> rows) {
        for (final var row : rows) {
            resources.add(this.electionResource(row));
        }
    }

    private List<RaceResource> races(final List<Race> rows) {
        final var resources = new ArrayList<RaceResource>();
        this.addRaces(resources, rows);
        return resources;
    }

    private void addRaces(final List<RaceResource> resources, final List<Race> rows) {
        for (final var row : rows) {
            resources.add(this.raceResource(row));
        }
    }

    private List<PersonResource> persons(final List<Person> rows) {
        final var resources = new ArrayList<PersonResource>();
        this.addPersons(resources, rows);
        return resources;
    }

    private void addPersons(final List<PersonResource> resources, final List<Person> rows) {
        for (final var row : rows) {
            resources.add(this.personResource(row));
        }
    }

    private List<CandidateResource> candidates(final List<Candidate> rows) {
        final var resources = new ArrayList<CandidateResource>();
        this.addCandidates(resources, rows);
        return resources;
    }

    private void addCandidates(final List<CandidateResource> resources, final List<Candidate> rows) {
        for (final var row : rows) {
            resources.add(this.candidateResource(row));
        }
    }

    private List<RaceCandidateResource> raceCandidates(final List<RaceCandidate> rows) {
        final var resources = new ArrayList<RaceCandidateResource>();
        this.addRaceCandidates(resources, rows);
        return resources;
    }

    private void addRaceCandidates(final List<RaceCandidateResource> resources, final List<RaceCandidate> rows) {
        for (final var row : rows) {
            resources.add(this.raceCandidateResource(row));
        }
    }

    private List<BallotStyleResource> ballotStyles(final List<BallotStyle> rows) {
        final var resources = new ArrayList<BallotStyleResource>();
        this.addBallotStyles(resources, rows);
        return resources;
    }

    private void addBallotStyles(final List<BallotStyleResource> resources, final List<BallotStyle> rows) {
        for (final var row : rows) {
            resources.add(this.ballotStyleResource(row));
        }
    }

    private List<PlaceResource> places(final List<RecordingPlace> rows) {
        final var resources = new ArrayList<PlaceResource>();
        this.addPlaces(resources, rows);
        return resources;
    }

    private void addPlaces(final List<PlaceResource> resources, final List<RecordingPlace> rows) {
        for (final var row : rows) {
            resources.add(this.placeResource(row));
        }
    }

    private List<DeviceResource> devices(final List<RecordingDevice> rows) {
        final var resources = new ArrayList<DeviceResource>();
        this.addDevices(resources, rows);
        return resources;
    }

    private void addDevices(final List<DeviceResource> resources, final List<RecordingDevice> rows) {
        for (final var row : rows) {
            resources.add(this.deviceResource(row));
        }
    }

    private List<AssignmentResource> assignments(final List<ElectionVotingPrecinctAssignment> rows) {
        final var resources = new ArrayList<AssignmentResource>();
        this.addAssignments(resources, rows);
        return resources;
    }

    private void addAssignments(
        final List<AssignmentResource> resources,
        final List<ElectionVotingPrecinctAssignment> rows
    ) {
        for (final var row : rows) {
            resources.add(this.assignmentResource(row));
        }
    }

    private LocalityResource localityResource(final Locality locality) {
        final var resource = new LocalityResource();
        resource.setId(locality.getId());
        resource.setTenantId(locality.getTenantId());
        resource.setName(locality.getName());
        resource.setType(locality.getType());
        resource.setParentId(locality.getParentId());
        return resource;
    }

    private AddressResource addressResource(final Address address) {
        final var resource = new AddressResource();
        resource.setId(address.getId());
        resource.setTenantId(address.getTenantId());
        resource.setLocalityId(address.getLocalityId());
        resource.setStreet1(address.getStreet1());
        resource.setPostalCode(address.getPostalCode());
        return resource;
    }

    private PrecinctResource precinctResource(final VotingPrecinct precinct) {
        final var resource = new PrecinctResource();
        resource.setId(precinct.getId());
        resource.setTenantId(precinct.getTenantId());
        resource.setName(precinct.getName());
        resource.setAddressId(precinct.getAddressId());
        return resource;
    }

    private DistrictResource districtResource(final VotingDistrict district) {
        final var resource = new DistrictResource();
        final var type = district.getType();
        resource.setId(district.getId());
        resource.setTenantId(district.getTenantId());
        resource.setLocalityId(district.getLocalityId());
        resource.setName(district.getName());
        resource.setType(this.wireText(type));
        return resource;
    }

    private PrecinctDistrictResource precinctDistrictResource(final PrecinctDistrict row) {
        final var resource = new PrecinctDistrictResource();
        final var type = row.getDistrictType();
        resource.setId(row.getId());
        resource.setTenantId(row.getTenantId());
        resource.setVotingPrecinctId(row.getVotingPrecinctId());
        resource.setVotingDistrictId(row.getVotingDistrictId());
        resource.setDistrictType(this.wireText(type));
        return resource;
    }

    private SeatResource seatResource(final Seat seat) {
        final var resource = new SeatResource();
        final var jurisdiction = seat.getJurisdiction();
        final var type = seat.getType();
        resource.setId(seat.getId());
        resource.setTenantId(seat.getTenantId());
        resource.setLocalityId(seat.getLocalityId());
        resource.setName(seat.getName());
        resource.setJurisdiction(this.wireText(jurisdiction));
        resource.setType(this.wireText(type));
        resource.setAppointments(seat.getAppointments());
        resource.setTermLength(seat.getTermLength());
        return resource;
    }

    private TermResource termResource(final Term term) {
        final var resource = new TermResource();
        resource.setId(term.getId());
        resource.setTenantId(term.getTenantId());
        resource.setSeatId(term.getSeatId());
        return resource;
    }

    private ElectionResource electionResource(final Election election) {
        final var resource = new ElectionResource();
        final var status = election.getStatus();
        final var demonstration = this.app.demonstration(election.getId());
        resource.setId(election.getId());
        resource.setTenantId(election.getTenantId());
        resource.setName(election.getName());
        resource.setStatus(status.wire());
        resource.setVotingStart(CanonicalJson.timestampText(election.getVotingStart()));
        resource.setVotingEnd(CanonicalJson.timestampText(election.getVotingEnd()));
        resource.setHash(election.getHash());
        resource.setSignature(election.getSignature());
        resource.setDemonstration(demonstration);
        return resource;
    }

    private RaceResource raceResource(final Race race) {
        final var resource = new RaceResource();
        final var type = race.getType();
        final var method = race.getMethod();
        resource.setId(race.getId());
        resource.setTenantId(race.getTenantId());
        resource.setElectionId(race.getElectionId());
        resource.setTermId(race.getTermId());
        resource.setName(race.getName());
        resource.setType(this.wireText(type));
        resource.setMethod(this.wireText(method));
        resource.setPreviousRaceId(race.getPreviousRaceId());
        resource.setRound(race.getRound());
        return resource;
    }

    private PersonResource personResource(final Person person) {
        final var resource = new PersonResource();
        resource.setId(person.getId());
        resource.setTenantId(person.getTenantId());
        resource.setFirstName(person.getFirstName());
        resource.setLastName(person.getLastName());
        return resource;
    }

    private CandidateResource candidateResource(final Candidate candidate) {
        final var resource = new CandidateResource();
        resource.setId(candidate.getId());
        resource.setTenantId(candidate.getTenantId());
        resource.setPersonId(candidate.getPersonId());
        resource.setPartyAffiliation(candidate.getPartyAffiliation());
        return resource;
    }

    private RaceCandidateResource raceCandidateResource(final RaceCandidate slot) {
        final var resource = new RaceCandidateResource();
        final var choice = slot.getReferendumChoice();
        resource.setId(slot.getId());
        resource.setTenantId(slot.getTenantId());
        resource.setRaceId(slot.getRaceId());
        resource.setCandidateId(slot.getCandidateId());
        resource.setWriteIn(slot.isWriteIn());
        resource.setReferendumChoice(this.wireText(choice));
        return resource;
    }

    private BallotStyleResource ballotStyleResource(final BallotStyle style) {
        final var resource = new BallotStyleResource();
        resource.setId(style.getId());
        resource.setTenantId(style.getTenantId());
        resource.setElectionId(style.getElectionId());
        resource.setVotingPrecinctId(style.getVotingPrecinctId());
        resource.setRaceId(style.getRaceId());
        return resource;
    }

    private PlaceResource placeResource(final RecordingPlace place) {
        final var resource = new PlaceResource();
        final var kind = place.getKind();
        resource.setId(place.getId());
        resource.setTenantId(place.getTenantId());
        resource.setLocalityId(place.getLocalityId());
        resource.setName(place.getName());
        resource.setKind(this.wireText(kind));
        return resource;
    }

    private DeviceResource deviceResource(final RecordingDevice device) {
        final var resource = new DeviceResource();
        final var kind = device.getKind();
        final var status = device.getStatus();
        resource.setId(device.getId());
        resource.setTenantId(device.getTenantId());
        resource.setName(device.getName());
        resource.setKind(this.wireText(kind));
        resource.setPublicKey(device.getPublicKey());
        resource.setStatus(this.wireText(status));
        return resource;
    }

    private AssignmentResource assignmentResource(final ElectionVotingPrecinctAssignment assignment) {
        final var resource = new AssignmentResource();
        final var status = assignment.getAssignmentStatus();
        resource.setId(assignment.getId());
        resource.setTenantId(assignment.getTenantId());
        resource.setElectionId(assignment.getElectionId());
        resource.setVotingPrecinctId(assignment.getVotingPrecinctId());
        resource.setAssignmentStatus(this.wireText(status));
        return resource;
    }

    private DemoCastResource demoCastResource(final DemoCast cast) {
        final var resource = new DemoCastResource();
        final var result = cast.result();
        resource.setCode(cast.code());
        resource.setBallot(this.ballotResource(result.ballot()));
        resource.setVotes(this.votes(result.votes()));
        return resource;
    }

    private List<VoteResource> votes(final List<Vote> rows) {
        final var resources = new ArrayList<VoteResource>();
        this.addVotes(resources, rows);
        return resources;
    }

    private void addVotes(final List<VoteResource> resources, final List<Vote> rows) {
        for (final var vote : rows) {
            resources.add(this.voteResource(vote));
        }
    }

    private BallotResource ballotResource(final Ballot ballot) {
        final var resource = new BallotResource();
        final var type = ballot.getType();
        resource.setId(ballot.getId());
        resource.setElectionId(ballot.getElectionId());
        resource.setVotingPrecinctId(ballot.getVotingPrecinctId());
        resource.setType(type.wire());
        resource.setRecordingDeviceId(ballot.getRecordingDeviceId());
        resource.setRecordingPlaceId(ballot.getRecordingPlaceId());
        resource.setRecordedBy(ballot.getRecordedBy());
        resource.setTimestamp(CanonicalJson.timestampText(ballot.getTimestamp()));
        resource.setHash(ballot.getHash());
        resource.setPayload(ballot.getPayload());
        return resource;
    }

    private VoteResource voteResource(final Vote vote) {
        final var resource = new VoteResource();
        resource.setId(vote.getId());
        resource.setRaceCandidateId(vote.getRaceCandidateId());
        resource.setValue(vote.getValue());
        resource.setHash(vote.getHash());
        return resource;
    }

    private String wireText(final WireValue value) {
        if (value == null) {
            return null;
        }
        else {
            return value.wire();
        }
    }

    private ReferendumChoice choice(final String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        else {
            return this.wire(ReferendumChoice.values(), text, "referendum choice");
        }
    }

    private AssignmentStatus assignmentStatus(final String text) {
        if (text == null || text.isBlank()) {
            return AssignmentStatus.CURRENT;
        }
        else {
            return this.wire(AssignmentStatus.values(), text, "assignment status");
        }
    }

    private <T> T read(final HttpExchange exchange, final Class<T> type) throws IOException {
        try {
            return this.readJson(exchange, type);
        }
        catch (final JsonProcessingException failure) {
            throw new ConstraintException("invalid json");
        }
    }

    private <T> T readJson(final HttpExchange exchange, final Class<T> type) throws IOException {
        final var input = exchange.getRequestBody();
        final var bytes = input.readAllBytes();
        return this.json.readValue(bytes, type);
    }

    private void writeJson(final HttpExchange exchange, final int status, final Object body) throws IOException {
        final var bytes = this.json.writeValueAsBytes(body);
        final var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        this.writeBody(exchange, bytes);
    }

    private void writeBody(final HttpExchange exchange, final byte[] bytes) throws IOException {
        final var output = exchange.getResponseBody();
        output.write(bytes);
        output.close();
    }

    private UUID id(final String text) {
        try {
            return UUID.fromString(text);
        }
        catch (final IllegalArgumentException failure) {
            throw new ConstraintException("invalid id");
        }
    }

    private Instant instant(final String text) {
        try {
            return Instant.parse(text);
        }
        catch (final DateTimeParseException | NullPointerException failure) {
            throw new ConstraintException("invalid timestamp");
        }
    }

    private <T extends WireValue> T wire(final T[] values, final String text, final String label) {
        try {
            return Wires.parse(values, text, label);
        }
        catch (final IllegalArgumentException failure) {
            throw new ConstraintException(failure.getMessage());
        }
    }

    static final class LocalityBody {
        public String name;
        public String type;
        public UUID parentId;
    }

    static final class AddressBody {
        public UUID localityId;
        public String street1;
        public String postalCode;
    }

    static final class PrecinctBody {
        public String name;
        public UUID addressId;
    }

    static final class DistrictBody {
        public UUID localityId;
        public String name;
        public String type;
    }

    static final class PrecinctDistrictBody {
        public UUID votingPrecinctId;
        public UUID votingDistrictId;
        public String districtType;
    }

    static final class SeatBody {
        public UUID localityId;
        public String name;
        public String jurisdiction;
        public String type;
        public int appointments;
        public int termLength;
    }

    static final class TermBody {
        public UUID seatId;
    }

    static final class RaceEditBody {
        public String name;
        public String method;
        public UUID previousRaceId;
    }

    static final class RaceBody {
        public UUID previousRaceId;
        public UUID electionId;
        public UUID termId;
        public String name;
        public String type;
        public String method;
    }

    static final class CandidateBody {
        public UUID personId;
        public String partyAffiliation;
    }

    static final class RaceCandidateBody {
        public UUID raceId;
        public UUID candidateId;
        public boolean writeIn;
        public String referendumChoice;
    }

    static final class BallotStyleBody {
        public UUID electionId;
        public UUID votingPrecinctId;
        public UUID raceId;
    }

    static final class PlaceBody {
        public UUID localityId;
        public String name;
        public String kind;
        public UUID addressId;
    }

    static final class DeviceBody {
        public String name;
        public String kind;
        public String publicKey;
        public String status;
    }

    static final class AssignmentBody {
        public UUID electionId;
        public UUID votingPrecinctId;
        public String assignmentStatus;
    }

    static final class ShowcaseElectionBody {
        public UUID localityId;
        public String name;
        public String votingStart;
        public String votingEnd;
    }

    static final class ShowcaseDeviceBody {
        public String name;
        public String kind;
    }

    static final class CastBody {
        public UUID votingPrecinctId;
        public UUID recordingDeviceId;
        public UUID recordingPlaceId;
        public UUID personId;
        public List<SelectionBody> selections;
    }

    static final class SelectionBody {
        public UUID raceCandidateId;
        public String value;
    }
}
