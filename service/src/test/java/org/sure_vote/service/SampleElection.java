package org.sure_vote.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.sure_vote.entity.Address;
import org.sure_vote.entity.AssignmentStatus;
import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.BallotIssuance;
import org.sure_vote.entity.BallotStyle;
import org.sure_vote.entity.BallotType;
import org.sure_vote.entity.ChainStatus;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.ElectionMethod;
import org.sure_vote.entity.ElectionStatus;
import org.sure_vote.entity.ElectionVotingPrecinctAssignment;
import org.sure_vote.entity.Locality;
import org.sure_vote.entity.Outcome;
import org.sure_vote.entity.Person;
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
import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.TenantKind;
import org.sure_vote.entity.TenantStatus;
import org.sure_vote.entity.Term;
import org.sure_vote.entity.Vote;
import org.sure_vote.entity.VoteChain;
import org.sure_vote.entity.VoteChainEntry;
import org.sure_vote.entity.VotingPrecinct;
import org.sure_vote.repository.InMemorySureVoteRepository;
import org.sure_vote.repository.SureVoteRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class SampleElection {
    private static final String VIEWING_KEY = "ab".repeat(32);

    final SureVoteRepository repository;
    final SureVote app;
    final UUID actor;
    final SigningKey operator;
    final SigningKey importKey;
    final SigningKey deviceKey;
    final Map<UUID, String> codes = new LinkedHashMap<>();
    Instant ballotTimestamp = Instant.parse("2026-11-03T12:00:00Z");
    Instant entryTimestamp = Instant.parse("2026-11-05T14:05:01.123Z");
    UUID tenantId;
    UUID localityId;
    UUID addressId;
    UUID precinctId;
    UUID placeId;
    UUID deviceId;
    UUID electionId;
    UUID seatId;
    UUID termId;
    UUID raceId;
    UUID extraRaceId;
    UUID slotA;
    UUID slotB;
    UUID writeIn;
    String lastCode;
    private int nonceCounter;
    private final Instant votingStart = Instant.parse("2026-11-03T00:00:00Z");
    private final Instant votingEnd = Instant.parse("2026-11-04T00:00:00Z");

    private SampleElection() {
        this.repository = new InMemorySureVoteRepository();
        this.app = new SureVote(this.repository);
        this.actor = UUID.randomUUID();
        this.operator = Signatures.generate();
        this.importKey = Signatures.generate();
        this.deviceKey = Signatures.generate();
    }

    static SampleElection showcase() {
        final var world = new SampleElection();
        world.tenantId = world.savedTenant(world.tenant(TenantKind.SHOWCASE));
        return world;
    }

    static SampleElection upcomingFptp() {
        final var world = new SampleElection();
        world.readyFptp();
        return world;
    }

    static SampleElection fptp() {
        final var world = SampleElection.upcomingFptp();
        world.activate();
        return world;
    }

    static SampleElection referendum() {
        final var world = new SampleElection();
        world.base();
        world.raceId = world.addRace(ElectionMethod.REFERENDUM, RaceType.GENERAL, "Levy");
        world.slotA = world.slot(world.raceId, "Yes", "Choice", false, ReferendumChoice.YES, null);
        world.slotB = world.slot(world.raceId, "No", "Choice", false, ReferendumChoice.NO, null);
        world.style(world.raceId);
        world.activate();
        return world;
    }

    static SampleElection upcomingRanked() {
        final var world = new SampleElection();
        world.base();
        world.raceId = world.addRace(ElectionMethod.RANKED_CHOICE, RaceType.GENERAL, "Mayor");
        world.slotA = world.slot(world.raceId, "Ada", "One", false, null, null);
        world.slotB = world.slot(world.raceId, "Bea", "Two", false, null, null);
        world.style(world.raceId);
        return world;
    }

    static SampleElection ranked() {
        final var world = SampleElection.upcomingRanked();
        world.activate();
        return world;
    }

    static SampleElection primary() {
        final var world = new SampleElection();
        world.base();
        world.raceId = world.addRace(ElectionMethod.FIRST_PAST_THE_POST, RaceType.PRIMARY, "Red primary");
        world.extraRaceId = world.addRace(ElectionMethod.FIRST_PAST_THE_POST, RaceType.PRIMARY, "Blue primary");
        world.slotA = world.slot(world.raceId, "Ada", "One", false, null, "Red");
        world.slotB = world.slot(world.extraRaceId, "Bea", "Two", false, null, "Blue");
        world.style(world.raceId);
        world.style(world.extraRaceId);
        world.activate();
        return world;
    }

    static SampleElection upcomingBare() {
        final var world = new SampleElection();
        world.base();
        return world;
    }

    static SampleElection unstyledRace() {
        final var world = new SampleElection();
        world.base();
        world.raceId = world.addRace(ElectionMethod.FIRST_PAST_THE_POST, RaceType.GENERAL, "Mayor");
        world.extraRaceId = world.addRace(ElectionMethod.FIRST_PAST_THE_POST, RaceType.GENERAL, "Clerk");
        world.slotA = world.slot(world.raceId, "Ada", "One", false, null, null);
        world.slotB = world.slot(world.extraRaceId, "Bea", "Two", false, null, null);
        world.style(world.raceId);
        world.activate();
        return world;
    }

    void readyFptp() {
        this.base();
        this.raceId = this.addRace(ElectionMethod.FIRST_PAST_THE_POST, RaceType.GENERAL, "Mayor");
        this.slotA = this.slot(this.raceId, "Ada", "One", false, null, null);
        this.slotB = this.slot(this.raceId, "Bea", "Two", false, null, null);
        this.writeIn = this.slot(this.raceId, "Winn", "In", true, null, null);
        this.style(this.raceId);
    }

    void activate() {
        final var hash = this.app.definitionHash(this.electionId);
        final var raw = Digests.unhex(hash);
        final var seed = this.operator.seed();
        final var signature = Signatures.sign(seed, raw);
        this.app.activate(this.electionId, signature, this.actor);
    }

    void close() {
        final var signatures = this.chainSignatures();
        final var canvassSignature = this.canvassSignature();
        this.app.close(this.electionId, signatures, canvassSignature, this.actor);
    }

    Map<UUID, String> chainSignatures() {
        final var signatures = new LinkedHashMap<UUID, String>();
        final var races = this.repository.list(Race.class);
        this.signRaces(signatures, races);
        return signatures;
    }

    String canvassSignature() {
        final var payload = this.app.canvassPayload(this.electionId);
        final var hash = Digests.sha256Hex(payload);
        final var raw = Digests.unhex(hash);
        final var seed = this.operator.seed();
        return Signatures.sign(seed, raw);
    }

    Ballot castOne(final UUID slotId, final String value) {
        final var selection = new SealSelection(slotId, value, this.nextNonce());
        return this.cast(List.of(selection));
    }

    Ballot castRanked(final List<UUID> slots) {
        final var selections = new ArrayList<SealSelection>();
        this.addRanks(selections, slots);
        return this.cast(selections);
    }

    private void addRanks(final List<SealSelection> selections, final List<UUID> slots) {
        var rank = 1;
        for (final var slot : slots) {
            final var value = Integer.toString(rank);
            final var nonce = this.nextNonce();
            selections.add(new SealSelection(slot, value, nonce));
            rank = rank + 1;
        }
    }

    Ballot cast(final List<SealSelection> selections) {
        final var sealed = this.sealedSelections(selections);
        final var ballot = this.app.importBallot(sealed);
        this.codes.put(ballot.getId(), this.lastCode);
        return ballot;
    }

    ImportBallot sealed(final UUID slotId, final String value) {
        final var selection = new SealSelection(slotId, value, this.nextNonce());
        return this.sealedSelections(List.of(selection));
    }

    ImportBallot typed(final BallotType type) {
        return new ImportBallot(
            this.tenantId,
            this.actor,
            this.electionId,
            this.precinctId,
            type,
            this.deviceId,
            this.placeId,
            this.deviceId,
            this.ballotTimestamp,
            null,
            null,
            null
        );
    }

    ImportBallot withSignature(final ImportBallot sealed, final String signature) {
        return new ImportBallot(
            sealed.tenantId(),
            sealed.actorId(),
            sealed.electionId(),
            sealed.votingPrecinctId(),
            sealed.type(),
            sealed.recordingDeviceId(),
            sealed.recordingPlaceId(),
            sealed.recordedBy(),
            sealed.timestamp(),
            sealed.voterAccessHash(),
            signature,
            sealed.votes()
        );
    }

    RecordingDevice device() {
        final var device = new RecordingDevice();
        final var key = this.deviceKey.publicKey();
        device.setTenantId(this.tenantId);
        device.setName("Machine 2");
        device.setKind(RecordingDeviceKind.POLLING_PLACE_MACHINE);
        device.setStatus(RecordingDeviceStatus.ACTIVE);
        device.setPublicKey(key);
        return device;
    }

    UUID person(final String first, final String last) {
        final var person = new Person();
        person.setTenantId(this.tenantId);
        person.setFirstName(first);
        person.setLastName(last);
        return this.savedPerson(person);
    }

    void secretPerson() {
        final var person = new Person();
        person.setTenantId(this.tenantId);
        person.setFirstName("Ada");
        person.setLastName("One");
        person.setSsn("123-45-6789");
        this.app.createPerson(person, this.actor);
    }

    void voterIdPerson() {
        final var person = new Person();
        person.setTenantId(this.tenantId);
        person.setFirstName("Bea");
        person.setLastName("Two");
        person.setVoterId("V-1");
        this.app.createPerson(person, this.actor);
    }

    UUID accept(final UUID personId) {
        final var issuance = new BallotIssuance();
        issuance.setTenantId(this.tenantId);
        issuance.setPersonId(personId);
        issuance.setElectionId(this.electionId);
        issuance.setRecordingPlaceId(this.placeId);
        return this.savedIssuance(issuance);
    }

    UUID reissue(final UUID personId, final UUID issuanceId) {
        this.app.voidIssuance(issuanceId, "machine not started", this.actor);
        return this.accept(personId);
    }

    void assertImported(final Ballot first, final Ballot second) {
        final var firstEntry = this.entryAt(0);
        final var secondEntry = this.entryAt(1);
        final var chain = this.chain(this.raceId);
        this.assertEntryPayload(firstEntry, null);
        this.assertEntryPayload(secondEntry, firstEntry.getHash());
        assertEquals(2, chain.getLength());
        assertEquals(secondEntry.getHash(), chain.getHash());
        assertNull(chain.getSignature());
        assertEquals(this.deviceId, first.getCreatedBy());
        assertEquals(this.deviceId, second.getCreatedBy());
        assertEquals(this.actor, firstEntry.getCreatedBy());
        assertEquals(this.actor, secondEntry.getCreatedBy());
        assertNull(firstEntry.getPreviousEntryId());
        assertEquals(firstEntry.getId(), secondEntry.getPreviousEntryId());
        this.assertHidden(first);
        this.assertHidden(second);
        this.assertBinding(first);
        this.assertLookup(first);
        this.assertLookup(second);
    }

    void assertOutcome(final UUID slotId, final Outcome outcome, final Integer rank, final int votes) {
        final var found = this.repository.find(RaceCandidate.class, slotId);
        final var slot = found.orElseThrow();
        assertEquals(votes, slot.getVotesReceived());
        assertEquals(outcome, slot.getOutcome());
        assertEquals(rank, slot.getRank());
        assertNull(slot.getVotePercentage());
    }

    void assertClosed() {
        final var election = this.app.election(this.electionId);
        final var chains = this.repository.list(VoteChain.class);
        assertEquals(ElectionStatus.HISTORICAL, election.getStatus());
        this.assertChainsClosed(chains);
    }

    void assertStillActive() {
        final var election = this.app.election(this.electionId);
        final var chain = this.chain(this.raceId);
        assertEquals(ElectionStatus.ACTIVE, election.getStatus());
        assertEquals(ChainStatus.ACTIVE, chain.getStatus());
        assertNull(chain.getSignature());
    }

    private void assertChainsClosed(final List<VoteChain> chains) {
        for (final var chain : chains) {
            assertEquals(ChainStatus.CLOSED, chain.getStatus());
            assertNotNull(chain.getSignature());
        }
    }

    private void assertHidden(final Ballot ballot) {
        final var payload = ballot.getPayload();
        final var code = this.codes.get(ballot.getId());
        assertFalse(payload.contains(code));
        assertEquals(Digests.sha256Hex(payload), ballot.getHash());
    }

    private void assertBinding(final Ballot ballot) {
        final var type = ballot.getType();
        final var typeWire = type.wire();
        final var binding = Payloads.binding(
            ballot.getElectionId(),
            ballot.getVotingPrecinctId(),
            typeWire,
            ballot.getRecordingDeviceId(),
            ballot.getRecordingPlaceId(),
            ballot.getRecordedBy(),
            ballot.getTimestamp(),
            ballot.getVoterAccessHash()
        );
        final var votes = this.votesFor(ballot.getId());
        final var vote = votes.getFirst();
        final var payload = vote.getPayload();
        assertFalse(binding.equals(ballot.getHash()));
        assertEquals(true, payload.contains("\"ballotHash\":\"" + binding + "\""));
    }

    private void assertLookup(final Ballot ballot) {
        final var code = this.codes.get(ballot.getId());
        final var found = this.app.lookup(this.electionId, code);
        final var lookedUp = found.ballot();
        final var votes = found.votes();
        assertEquals(ballot.getId(), lookedUp.getId());
        assertEquals(1, votes.size());
    }

    private void assertEntryPayload(final VoteChainEntry entry, final String previous) {
        final var found = this.repository.find(Vote.class, entry.getVoteId());
        final var vote = found.orElseThrow();
        final var expected = Payloads.entry(
            vote.getHash(),
            previous,
            entry.getIndex(),
            entry.getTimestamp(),
            entry.getNonce()
        );
        assertEquals(expected, entry.getPayload());
        assertEquals(Digests.sha256Hex(entry.getPayload()), entry.getHash());
    }

    private void base() {
        this.tenantId = this.savedTenant(this.tenant(TenantKind.PRODUCTION));
        this.localityId = this.savedLocality(this.locality());
        this.addressId = this.savedAddress(this.address());
        this.precinctId = this.savedPrecinct(this.precinct());
        this.placeId = this.savedPlace(this.place());
        this.deviceId = this.savedDevice(this.recordingDevice());
        this.electionId = this.savedElection(this.election());
        this.seatId = this.savedSeat(this.seat());
        this.termId = this.savedTerm(this.term());
        this.assign();
    }

    private Tenant tenant(final TenantKind kind) {
        final var tenant = new Tenant();
        tenant.setName("Town");
        tenant.setKind(kind);
        tenant.setResidencyRegion("us-east");
        tenant.setStatus(TenantStatus.ACTIVE);
        return tenant;
    }

    private Locality locality() {
        final var locality = new Locality();
        locality.setTenantId(this.tenantId);
        locality.setName("Town");
        locality.setType("city");
        return locality;
    }

    private Address address() {
        final var address = new Address();
        address.setTenantId(this.tenantId);
        address.setLocalityId(this.localityId);
        address.setStreet1("1 Main");
        address.setPostalCode("00000");
        return address;
    }

    private VotingPrecinct precinct() {
        final var precinct = new VotingPrecinct();
        precinct.setTenantId(this.tenantId);
        precinct.setName("Precinct 1");
        precinct.setAddressId(this.addressId);
        return precinct;
    }

    private RecordingPlace place() {
        final var place = new RecordingPlace();
        place.setTenantId(this.tenantId);
        place.setLocalityId(this.localityId);
        place.setName("Town Hall");
        place.setKind(RecordingPlaceKind.POLLING_PLACE);
        place.setAddressId(this.addressId);
        return place;
    }

    private RecordingDevice recordingDevice() {
        final var device = new RecordingDevice();
        final var key = this.deviceKey.publicKey();
        device.setTenantId(this.tenantId);
        device.setName("Machine 1");
        device.setKind(RecordingDeviceKind.POLLING_PLACE_MACHINE);
        device.setStatus(RecordingDeviceStatus.ACTIVE);
        device.setPublicKey(key);
        return device;
    }

    private Election election() {
        final var election = new Election();
        final var operatorKey = this.operator.publicKey();
        final var importKeyHex = this.importKey.publicKey();
        election.setTenantId(this.tenantId);
        election.setLocalityId(this.localityId);
        election.setName("General");
        election.setVotingStart(this.votingStart);
        election.setVotingEnd(this.votingEnd);
        election.setOperatorPublicKey(operatorKey);
        election.setImportPublicKey(importKeyHex);
        election.setViewingPublicKey(VIEWING_KEY);
        return election;
    }

    private Seat seat() {
        final var seat = new Seat();
        seat.setTenantId(this.tenantId);
        seat.setLocalityId(this.localityId);
        seat.setName("Mayor");
        seat.setJurisdiction(SeatJurisdiction.LOCAL);
        seat.setType(SeatType.MAYOR);
        seat.setAppointments(1);
        seat.setTermLength(4);
        return seat;
    }

    private Term term() {
        final var term = new Term();
        term.setTenantId(this.tenantId);
        term.setSeatId(this.seatId);
        return term;
    }

    private void assign() {
        final var assignment = new ElectionVotingPrecinctAssignment();
        assignment.setTenantId(this.tenantId);
        assignment.setElectionId(this.electionId);
        assignment.setVotingPrecinctId(this.precinctId);
        assignment.setAssignmentStatus(AssignmentStatus.CURRENT);
        this.app.assignPrecinct(assignment, this.actor);
    }

    UUID addRace(final ElectionMethod method, final RaceType type, final String name) {
        final var race = new Race();
        race.setTenantId(this.tenantId);
        race.setElectionId(this.electionId);
        race.setTermId(this.termId);
        race.setName(name);
        race.setMethod(method);
        race.setType(type);
        return this.savedRace(race);
    }

    UUID slot(
        final UUID race,
        final String first,
        final String last,
        final boolean writeIn,
        final ReferendumChoice choice,
        final String party
    ) {
        final var personId = this.person(first, last);
        return this.saveSlot(race, personId, writeIn, choice, party);
    }

    private UUID saveSlot(
        final UUID race,
        final UUID personId,
        final boolean writeIn,
        final ReferendumChoice choice,
        final String party
    ) {
        final var candidateId = this.candidate(personId, party);
        final var slot = new RaceCandidate();
        slot.setTenantId(this.tenantId);
        slot.setRaceId(race);
        slot.setCandidateId(candidateId);
        slot.setWriteIn(writeIn);
        slot.setReferendumChoice(choice);
        return this.savedSlot(slot);
    }

    private UUID candidate(final UUID personId, final String party) {
        final var candidate = new org.sure_vote.entity.Candidate();
        candidate.setTenantId(this.tenantId);
        candidate.setPersonId(personId);
        candidate.setPartyAffiliation(party);
        return this.savedCandidate(candidate);
    }

    void style(final UUID race) {
        final var style = new BallotStyle();
        style.setTenantId(this.tenantId);
        style.setElectionId(this.electionId);
        style.setVotingPrecinctId(this.precinctId);
        style.setRaceId(race);
        this.app.createBallotStyle(style, this.actor);
    }

    private ImportBallot sealedSelections(final List<SealSelection> selections) {
        final var raw = AccessCodes.generate();
        final var code = AccessCodes.encode(raw);
        final var hash = AccessCodes.inPersonHash(raw);
        final var request = this.sealRequest(hash, selections);
        this.lastCode = code;
        return Sealer.seal(this.repository, request, this.deviceKey, this.importKey);
    }

    private SealRequest sealRequest(final String hash, final List<SealSelection> selections) {
        return new SealRequest(
            this.tenantId,
            this.actor,
            this.electionId,
            this.precinctId,
            BallotType.IN_PERSON,
            this.deviceId,
            this.placeId,
            this.deviceId,
            this.ballotTimestamp,
            hash,
            this.entryTimestamp,
            selections
        );
    }

    private String nextNonce() {
        this.nonceCounter = this.nonceCounter + 1;
        return "n-" + this.nonceCounter;
    }

    private void signRaces(final Map<UUID, String> signatures, final List<Race> races) {
        for (final var race : races) {
            this.signRace(signatures, race.getId());
        }
    }

    private void signRace(final Map<UUID, String> signatures, final UUID race) {
        final var chain = this.chain(race);
        final var raw = Digests.unhex(chain.getHash());
        final var seed = this.operator.seed();
        final var signature = Signatures.sign(seed, raw);
        signatures.put(race, signature);
    }

    private VoteChain chain(final UUID race) {
        final var chains = this.repository.list(VoteChain.class);
        return this.findChain(chains, race);
    }

    private VoteChain findChain(final List<VoteChain> chains, final UUID race) {
        for (final var chain : chains) {
            final var matched = this.matchChain(chain, race);
            if (matched != null) {
                return matched;
            }
        }
        throw new IllegalStateException("missing chain");
    }

    private VoteChain matchChain(final VoteChain chain, final UUID race) {
        final var chainRace = chain.getRaceId();
        if (race.equals(chainRace)) {
            return chain;
        }
        else {
            return null;
        }
    }

    private VoteChainEntry entryAt(final int index) {
        final var entries = this.repository.list(VoteChainEntry.class);
        return this.findEntry(entries, index);
    }

    private VoteChainEntry findEntry(final List<VoteChainEntry> entries, final int index) {
        for (final var entry : entries) {
            final var matched = this.matchEntry(entry, index);
            if (matched != null) {
                return matched;
            }
        }
        throw new IllegalStateException("missing entry");
    }

    private VoteChainEntry matchEntry(final VoteChainEntry entry, final int index) {
        if (entry.getIndex() == index) {
            return entry;
        }
        else {
            return null;
        }
    }

    private List<Vote> votesFor(final UUID ballotId) {
        final var votes = this.repository.list(Vote.class);
        final var matched = new ArrayList<Vote>();
        this.collectVotes(matched, votes, ballotId);
        return matched;
    }

    private void collectVotes(final List<Vote> matched, final List<Vote> votes, final UUID ballotId) {
        for (final var vote : votes) {
            this.collectVote(matched, vote, ballotId);
        }
    }

    private void collectVote(final List<Vote> matched, final Vote vote, final UUID ballotId) {
        final var voteBallot = vote.getBallotId();
        if (ballotId.equals(voteBallot)) {
            matched.add(vote);
        }
    }

    private UUID savedTenant(final Tenant tenant) {
        final var saved = this.app.createTenant(tenant, this.actor);
        return saved.getId();
    }

    private UUID savedLocality(final Locality locality) {
        final var saved = this.app.createLocality(locality, this.actor);
        return saved.getId();
    }

    private UUID savedAddress(final Address address) {
        final var saved = this.app.createAddress(address, this.actor);
        return saved.getId();
    }

    private UUID savedPrecinct(final VotingPrecinct precinct) {
        final var saved = this.app.createPrecinct(precinct, this.actor);
        return saved.getId();
    }

    private UUID savedPlace(final RecordingPlace place) {
        final var saved = this.app.createRecordingPlace(place, this.actor);
        return saved.getId();
    }

    private UUID savedDevice(final RecordingDevice device) {
        final var saved = this.app.createRecordingDevice(device, this.actor);
        return saved.getId();
    }

    private UUID savedElection(final Election election) {
        final var saved = this.app.createElection(election, this.actor);
        return saved.getId();
    }

    private UUID savedSeat(final Seat seat) {
        final var saved = this.app.createSeat(seat, this.actor);
        return saved.getId();
    }

    private UUID savedTerm(final Term term) {
        final var saved = this.app.createTerm(term, this.actor);
        return saved.getId();
    }

    private UUID savedRace(final Race race) {
        final var saved = this.app.createRace(race, this.actor);
        return saved.getId();
    }

    private UUID savedPerson(final Person person) {
        final var saved = this.app.createPerson(person, this.actor);
        return saved.getId();
    }

    private UUID savedCandidate(final org.sure_vote.entity.Candidate candidate) {
        final var saved = this.app.createCandidate(candidate, this.actor);
        return saved.getId();
    }

    private UUID savedSlot(final RaceCandidate slot) {
        final var saved = this.app.createRaceCandidate(slot, this.actor);
        return saved.getId();
    }

    private UUID savedIssuance(final BallotIssuance issuance) {
        final var saved = this.app.acceptVoter(issuance, this.actor);
        return saved.getId();
    }
}
