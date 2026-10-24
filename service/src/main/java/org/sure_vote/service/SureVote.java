package org.sure_vote.service;

import java.util.Map;
import java.util.UUID;

import org.sure_vote.entity.Address;
import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.BallotIssuance;
import org.sure_vote.entity.BallotStyle;
import org.sure_vote.entity.Candidate;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.ElectionVotingPrecinctAssignment;
import org.sure_vote.entity.Locality;
import org.sure_vote.entity.Person;
import org.sure_vote.entity.PrecinctDistrict;
import org.sure_vote.entity.Race;
import org.sure_vote.entity.RaceCandidate;
import org.sure_vote.entity.RecordingDevice;
import org.sure_vote.entity.RecordingPlace;
import org.sure_vote.entity.Seat;
import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.Term;
import org.sure_vote.entity.VotingPrecinct;
import org.sure_vote.repository.SureVoteRepository;

public final class SureVote {
    private final DefinitionService definitions;
    private final RecordingService recording;
    private final LookupService lookup;
    private final TallyService tally;

    public SureVote(final SureVoteRepository repository) {
        final var stores = new Stores(repository);
        final var electionDefinitions = new ElectionDefinitions();
        this.definitions = new DefinitionService(repository, stores, electionDefinitions);
        this.recording = new RecordingService(repository, stores, electionDefinitions);
        this.lookup = new LookupService(repository, stores);
        this.tally = new TallyService(repository, stores);
    }

    public Tenant createTenant(final Tenant tenant, final UUID actor) {
        return this.definitions.createTenant(tenant, actor);
    }

    public Person createPerson(final Person person, final UUID actor) {
        return this.definitions.createPerson(person, actor);
    }

    public Locality createLocality(final Locality locality, final UUID actor) {
        return this.definitions.createLocality(locality, actor);
    }

    public Address createAddress(final Address address, final UUID actor) {
        return this.definitions.createAddress(address, actor);
    }

    public VotingPrecinct createPrecinct(final VotingPrecinct precinct, final UUID actor) {
        return this.definitions.createPrecinct(precinct, actor);
    }

    public PrecinctDistrict createPrecinctDistrict(final PrecinctDistrict row, final UUID actor) {
        return this.definitions.createPrecinctDistrict(row, actor);
    }

    public Seat createSeat(final Seat seat, final UUID actor) {
        return this.definitions.createSeat(seat, actor);
    }

    public Term createTerm(final Term term, final UUID actor) {
        return this.definitions.createTerm(term, actor);
    }

    public Election createElection(final Election election, final UUID actor) {
        return this.definitions.createElection(election, actor);
    }

    public Race createRace(final Race race, final UUID actor) {
        return this.definitions.createRace(race, actor);
    }

    public Candidate createCandidate(final Candidate candidate, final UUID actor) {
        return this.definitions.createCandidate(candidate, actor);
    }

    public RaceCandidate createRaceCandidate(final RaceCandidate slot, final UUID actor) {
        return this.definitions.createRaceCandidate(slot, actor);
    }

    public BallotStyle createBallotStyle(final BallotStyle style, final UUID actor) {
        return this.definitions.createBallotStyle(style, actor);
    }

    public RecordingPlace createRecordingPlace(final RecordingPlace place, final UUID actor) {
        return this.definitions.createRecordingPlace(place, actor);
    }

    public RecordingDevice createRecordingDevice(final RecordingDevice device, final UUID actor) {
        return this.definitions.createRecordingDevice(device, actor);
    }

    public ElectionVotingPrecinctAssignment assignPrecinct(
        final ElectionVotingPrecinctAssignment assignment,
        final UUID actor
    ) {
        return this.definitions.assignPrecinct(assignment, actor);
    }

    public BallotIssuance acceptVoter(final BallotIssuance issuance, final UUID actor) {
        return this.definitions.acceptVoter(issuance, actor);
    }

    public BallotIssuance voidIssuance(final UUID id, final String reason, final UUID actor) {
        return this.definitions.voidIssuance(id, reason, actor);
    }

    public String definitionHash(final UUID electionId) {
        return this.definitions.definitionHash(electionId);
    }

    public Election activate(final UUID electionId, final String signature, final UUID actor) {
        return this.definitions.activate(electionId, signature, actor);
    }

    public Election election(final UUID id) {
        return this.definitions.election(id);
    }

    public Ballot importBallot(final ImportBallot request) {
        return this.recording.importBallot(request);
    }

    public Person sealViewingPassphrase(
        final UUID personId,
        final String plaintext,
        final String viewingPublicKey,
        final UUID actor
    ) {
        return this.definitions.sealViewingPassphrase(personId, plaintext, viewingPublicKey, actor);
    }

    public LookupResult lookup(final UUID electionId, final String code) {
        return this.lookup(electionId, code, null);
    }

    public LookupResult lookup(final UUID electionId, final String code, final String passphrase) {
        return this.lookup.lookup(electionId, code, passphrase);
    }

    public String canvassPayload(final UUID electionId) {
        return this.tally.canvassPayload(electionId);
    }

    public Election close(
        final UUID electionId,
        final Map<UUID, String> signatures,
        final String canvassSignature,
        final UUID actor
    ) {
        return this.tally.close(electionId, signatures, canvassSignature, actor);
    }
}
