package org.sure_vote.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.sure_vote.entity.Address;
import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.BallotDispute;
import org.sure_vote.entity.BallotIssuance;
import org.sure_vote.entity.BallotStyle;
import org.sure_vote.entity.Candidate;
import org.sure_vote.entity.DisputeStatus;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.ElectionVotingPrecinctAssignment;
import org.sure_vote.entity.Locality;
import org.sure_vote.entity.Person;
import org.sure_vote.entity.PrecinctDistrict;
import org.sure_vote.entity.Race;
import org.sure_vote.entity.RaceCandidate;
import org.sure_vote.entity.RecordingDevice;
import org.sure_vote.entity.RecordingPlace;
import org.sure_vote.entity.RestoreIncident;
import org.sure_vote.entity.Seat;
import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.TenantValidator;
import org.sure_vote.entity.Term;
import org.sure_vote.entity.VoteChainValidationResult;
import org.sure_vote.entity.VoteChainValidator;
import org.sure_vote.entity.VotingDistrict;
import org.sure_vote.entity.VotingPrecinct;
import org.sure_vote.repository.SureVoteRepository;

public final class SureVote {
    private final DefinitionService definitions;
    private final RecordingService recording;
    private final LookupService lookup;
    private final TallyService tally;
    private final DisputeService disputes;
    private final ShowcaseService showcase;
    private final WorkspaceCatalog catalog;
    private final ValidatorService validators;
    private final RestoreService restores;

    public SureVote(final SureVoteRepository repository) {
        final var stores = new Stores(repository);
        final var electionDefinitions = new ElectionDefinitions();
        this.definitions = new DefinitionService(repository, stores, electionDefinitions);
        this.recording = new RecordingService(repository, stores, electionDefinitions);
        this.lookup = new LookupService(repository, stores);
        this.tally = new TallyService(repository, stores);
        this.disputes = new DisputeService(repository, stores, this.lookup);
        this.showcase = new ShowcaseService(repository, stores, this);
        this.catalog = new WorkspaceCatalog(repository, stores);
        this.validators = new ValidatorService(repository, stores);
        this.restores = new RestoreService(repository, stores, this.validators);
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

    public VotingDistrict createVotingDistrict(final VotingDistrict district, final UUID actor) {
        return this.definitions.createVotingDistrict(district, actor);
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

    public boolean demonstration(final UUID electionId) {
        return this.definitions.demonstration(electionId);
    }

    public WorkspaceView workspace(final UUID tenantId, final UUID actor) {
        return this.catalog.load(tenantId, actor);
    }

    public Election createShowcaseElection(final Election election, final UUID actor) {
        return this.showcase.createElection(election, actor);
    }

    public RecordingDevice createShowcaseDevice(final RecordingDevice device, final UUID actor) {
        return this.showcase.createDevice(device, actor);
    }

    public Election activateShowcase(final UUID electionId, final UUID actor) {
        return this.showcase.activate(electionId, actor);
    }

    public DemoCast castShowcase(
        final UUID electionId,
        final UUID precinctId,
        final UUID deviceId,
        final UUID placeId,
        final UUID personId,
        final List<DemoSelection> selections,
        final UUID actor
    ) {
        return this.showcase.cast(electionId, precinctId, deviceId, placeId, personId, selections, actor);
    }

    public Election closeShowcase(final UUID electionId, final UUID actor) {
        return this.showcase.close(electionId, actor);
    }

    public VoteChainValidator registerValidator(final VoteChainValidator validator, final UUID actor) {
        return this.validators.register(validator, actor);
    }

    public TenantValidator registerTenantValidator(final TenantValidator link, final UUID actor) {
        return this.validators.link(link, actor);
    }

    public VoteChainValidationResult fileValidation(
        final UUID electionId,
        final ValidationRequest request,
        final UUID actor
    ) {
        return this.validators.file(electionId, request, actor);
    }

    public List<HeadNotice> heads(final UUID electionId) {
        return this.validators.heads(electionId);
    }

    public List<VoteChainValidationResult> validationResults(final UUID electionId) {
        return this.validators.results(electionId);
    }

    public List<VoteChainValidator> electionValidators(final UUID electionId) {
        return this.validators.validators(electionId);
    }

    public String restoreIncidentPayload(final UUID electionId, final String reason, final Instant timestamp) {
        return this.restores.payload(electionId, reason, timestamp);
    }

    public RestoreIncident fileRestoreIncident(
        final UUID electionId,
        final RestoreRequest request,
        final UUID actor
    ) {
        return this.restores.file(electionId, request, actor);
    }

    public List<RestoreIncident> restoreIncidents(final UUID electionId) {
        return this.restores.incidents(electionId);
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

    public BallotDispute fileDispute(
        final UUID electionId,
        final String code,
        final String passphrase,
        final String statement
    ) {
        return this.disputes.file(electionId, code, passphrase, statement);
    }

    public List<BallotDispute> disputesForCode(
        final UUID electionId,
        final String code,
        final String passphrase
    ) {
        return this.disputes.forCode(electionId, code, passphrase);
    }

    public int disputeCount(final UUID electionId) {
        return this.disputes.count(electionId);
    }

    public List<BallotDispute> listDisputes(final UUID electionId, final UUID actor) {
        return this.disputes.list(electionId, actor);
    }

    public BallotDispute setDisputeStatus(final UUID id, final DisputeStatus status, final UUID actor) {
        return this.disputes.setStatus(id, status, actor);
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
