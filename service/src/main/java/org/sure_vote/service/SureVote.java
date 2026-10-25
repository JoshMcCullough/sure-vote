package org.sure_vote.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

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
import org.sure_vote.entity.PrincipalKind;
import org.sure_vote.entity.PrecinctDistrict;
import org.sure_vote.entity.Race;
import org.sure_vote.entity.RaceCandidate;
import org.sure_vote.entity.RecordingDevice;
import org.sure_vote.entity.RecordingPlace;
import org.sure_vote.entity.RestoreIncident;
import org.sure_vote.entity.Seat;
import org.sure_vote.entity.SystemAdmin;
import org.sure_vote.entity.SystemAdminStatus;
import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.TenantEntity;
import org.sure_vote.entity.TenantOfficial;
import org.sure_vote.entity.TenantOfficialStatus;
import org.sure_vote.entity.TenantStatus;
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
    private final SystemAdminService system;
    private final SessionService sessions;
    private final OfficialService officials;
    private final PasskeyService passkeys;
    private final SignedRequests signedRequests;
    private final SureVoteRepository repository;
    private volatile boolean devSignIn;

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
        this.system = new SystemAdminService(repository, stores);
        this.sessions = new SessionService(repository, stores);
        this.officials = new OfficialService(repository, stores, this.system);
        this.passkeys = new PasskeyService(repository, stores, this.sessions);
        this.signedRequests = new SignedRequests(repository, stores);
        this.repository = repository;
    }

    private <T> T published(final UUID electionId, final Supplier<T> body) {
        final var tenant = this.repository.electionTenant(electionId);
        if (tenant.isEmpty()) {
            throw new ConstraintException("Missing Election");
        }
        else {
            return this.repository.inTenant(tenant.get(), body);
        }
    }

    public SystemAdmin bootstrapSystemAdmin(final SystemAdmin admin) {
        return this.system.bootstrap(admin);
    }

    public SystemAdmin bootstrapSystemAdmin(final SystemAdmin admin, final String enrollmentToken) {
        final var saved = this.system.bootstrap(admin);
        this.officials.bootstrap(saved.getId(), enrollmentToken);
        return saved;
    }

    public void configurePasskeys(final PasskeySettings settings) {
        this.passkeys.configure(settings);
    }

    public boolean allowedOrigin(final String origin) {
        return this.passkeys.allowedOrigin(origin);
    }

    public void enableDevSignIn() {
        this.devSignIn = true;
    }

    public boolean devSignInEnabled() {
        return this.devSignIn;
    }

    public Principal session(final String token) {
        return this.sessions.resolve(token);
    }

    public void signOut(final String token) {
        this.sessions.revoke(token);
    }

    public String devSignIn(final PrincipalKind kind, final UUID id, final UUID tenantId) {
        if (!this.devSignIn) {
            throw new AccessException(403, "dev sign-in is disabled");
        }
        else {
            this.stores(kind != null && kind != PrincipalKind.VISITOR && id != null, "principal is required");
            return this.sessions.open(this.sessions.principal(kind, id, tenantId));
        }
    }

    public String visitorSession() {
        final var tenant = this.system.demoTenant();
        final var demo = tenant.orElseThrow(() -> new ConstraintException("no demo tenant"));
        return this.sessions.open(this.sessions.visitor(demo.getId()));
    }

    public PasskeyChallenge startEnrollment(final String token) {
        return this.passkeys.startEnrollment(token);
    }

    public PasskeyChallenge startAddingPasskey(final Principal principal) {
        return this.passkeys.startAdding(principal);
    }

    public String finishPasskeyRegistration(final UUID ceremonyId, final String response) {
        return this.passkeys.finishRegistration(ceremonyId, response);
    }

    public PasskeyChallenge startSignIn() {
        return this.passkeys.startSignIn();
    }

    public String finishSignIn(final UUID ceremonyId, final String response) {
        return this.passkeys.finishSignIn(ceremonyId, response);
    }

    public OfficialEnrollment createOfficial(final UUID tenantId, final String name, final Principal actor) {
        return this.officials.create(tenantId, name, actor);
    }

    public List<TenantOfficial> officials(final UUID tenantId, final Principal actor) {
        return this.officials.list(tenantId, actor);
    }

    public TenantOfficial setOfficialStatus(
        final UUID tenantId,
        final UUID id,
        final TenantOfficialStatus status,
        final Principal actor
    ) {
        return this.officials.setStatus(tenantId, id, status, actor);
    }

    public String officialEnrollment(final UUID tenantId, final UUID id, final Principal actor) {
        return this.officials.enroll(tenantId, id, actor);
    }

    public String adminEnrollment(final UUID adminId, final Principal actor) {
        return this.officials.enrollAdmin(adminId, actor);
    }

    public <T> T asTenant(final Principal principal, final Supplier<T> work) {
        if (principal == null || !principal.inTenant()) {
            throw AccessException.denied();
        }
        else {
            return this.repository.inTenant(principal.tenantId(), work);
        }
    }

    public <T> T inElection(final Principal principal, final UUID electionId, final Supplier<T> work) {
        return this.asTenantRow(principal, Election.class, electionId, work);
    }

    public <T> T asTenantRow(
        final Principal principal,
        final Class<? extends TenantEntity> type,
        final UUID id,
        final Supplier<T> work
    ) {
        return this.asTenant(principal, () -> this.owned(principal, type, this.present(id), work));
    }

    private <T> T owned(
        final Principal principal,
        final Class<? extends TenantEntity> type,
        final UUID id,
        final Supplier<T> work
    ) {
        final var tenantId = principal.tenantId();
        final var found = this.repository.find(type, id);
        final var foreign = found.filter(row -> !tenantId.equals(row.getTenantId()));
        if (foreign.isPresent()) {
            throw AccessException.denied();
        }
        else {
            return work.get();
        }
    }

    public <T> T asImport(final SignedRequest request, final UUID electionId, final BiFunction<UUID, UUID, T> work) {
        final var tenant = this.repository.electionTenant(this.present(electionId));
        final var tenantId = tenant.orElseThrow(() -> new ConstraintException("Missing Election"));
        return this.repository.inTenant(tenantId, () -> this.imported(request, electionId, tenantId, work));
    }

    public <T> T asValidator(
        final SignedRequest request,
        final UUID electionId,
        final UUID validatorId,
        final Supplier<T> work
    ) {
        final var validator = this.repository.find(VoteChainValidator.class, this.present(validatorId));
        final var live = validator.filter(row -> row.getDeletedAt() == null);
        final var key = live.map(VoteChainValidator::getPublicKey);
        final var publicKey = key.orElseThrow(AccessException::signIn);
        return this.published(this.present(electionId), () -> this.verified(request, validatorId, publicKey, work));
    }

    private <T> T verified(
        final SignedRequest request,
        final UUID validatorId,
        final String publicKey,
        final Supplier<T> work
    ) {
        this.signedRequests.verify(request, "validator:" + validatorId, publicKey);
        return work.get();
    }

    private <T> T imported(
        final SignedRequest request,
        final UUID electionId,
        final UUID tenantId,
        final BiFunction<UUID, UUID, T> work
    ) {
        final var found = this.repository.find(Election.class, electionId);
        final var election = found.orElseThrow(() -> new ConstraintException("Missing Election"));
        final var key = election.getImportPublicKey();
        final var keyId = "import:" + electionId;
        this.signedRequests.verify(request, keyId, key);
        return work.apply(tenantId, Tokens.derived("key", keyId));
    }

    private UUID present(final UUID id) {
        this.stores(id != null, "id is required");
        return id;
    }

    private void stores(final boolean condition, final String message) {
        if (!condition) {
            throw new ConstraintException(message);
        }
    }

    public SystemAdmin createSystemAdmin(final SystemAdmin admin, final UUID actor) {
        return this.system.createAdmin(admin, actor);
    }

    public List<SystemAdmin> systemAdmins(final UUID actor) {
        return this.system.admins(actor);
    }

    public SystemAdmin setSystemAdminStatus(final UUID id, final SystemAdminStatus status, final UUID actor) {
        return this.system.setAdminStatus(id, status, actor);
    }

    public Tenant createSystemTenant(final Tenant tenant, final UUID actor) {
        return this.system.createTenant(tenant, actor);
    }

    public List<Tenant> systemTenants(final UUID actor) {
        return this.system.tenants(actor);
    }

    public Tenant updateSystemTenant(final UUID id, final String name, final TenantStatus status, final UUID actor) {
        return this.system.updateTenant(id, name, status, actor);
    }

    public Tenant provisionDemoTenant(final UUID id, final String name, final String residencyRegion) {
        return this.system.provisionDemo(id, name, residencyRegion);
    }

    public Optional<Tenant> demoTenant() {
        return this.system.demoTenant();
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
        return this.published(id, () -> this.definitions.election(id));
    }

    public boolean demonstration(final UUID electionId) {
        return this.published(electionId, () -> this.definitions.demonstration(electionId));
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
        return this.published(electionId, () -> this.validators.heads(electionId));
    }

    public List<VoteChainValidationResult> validationResults(final UUID electionId) {
        return this.published(electionId, () -> this.validators.results(electionId));
    }

    public List<VoteChainValidator> electionValidators(final UUID electionId) {
        return this.published(electionId, () -> this.validators.validators(electionId));
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
        return this.published(electionId, () -> this.restores.incidents(electionId));
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

    public LookupResult lookup(final String code, final String passphrase) {
        return this.lookup.lookup(null, code, passphrase);
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
        return this.published(electionId, () -> this.disputes.count(electionId));
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
