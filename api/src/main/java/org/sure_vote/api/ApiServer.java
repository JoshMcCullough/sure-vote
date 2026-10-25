package org.sure_vote.api;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.BallotDispute;
import org.sure_vote.entity.BallotIssuance;
import org.sure_vote.entity.BallotType;
import org.sure_vote.entity.DisputeStatus;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.Person;
import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.TenantKind;
import org.sure_vote.entity.TenantStatus;
import org.sure_vote.entity.TenantValidator;
import org.sure_vote.entity.RestoreIncident;
import org.sure_vote.entity.ValidationResultStatus;
import org.sure_vote.entity.Vote;
import org.sure_vote.entity.VoteChainValidationResult;
import org.sure_vote.entity.VoteChainValidator;
import org.sure_vote.entity.WireValue;
import org.sure_vote.entity.Wires;
import org.sure_vote.resource.BallotResource;
import org.sure_vote.resource.DisputeCountResource;
import org.sure_vote.resource.DisputeListResource;
import org.sure_vote.resource.DisputeResource;
import org.sure_vote.resource.ElectionResource;
import org.sure_vote.resource.ErrorResource;
import org.sure_vote.resource.HashResource;
import org.sure_vote.resource.HeadListResource;
import org.sure_vote.resource.HeadResource;
import org.sure_vote.resource.HealthResource;
import org.sure_vote.resource.IssuanceResource;
import org.sure_vote.resource.LookupResource;
import org.sure_vote.resource.PersonResource;
import org.sure_vote.resource.TenantResource;
import org.sure_vote.resource.TenantValidatorResource;
import org.sure_vote.resource.RestoreIncidentListResource;
import org.sure_vote.resource.RestoreIncidentResource;
import org.sure_vote.resource.ValidationListResource;
import org.sure_vote.resource.ValidationResource;
import org.sure_vote.resource.ValidatorListResource;
import org.sure_vote.resource.ValidatorResource;
import org.sure_vote.resource.VoteResource;
import org.sure_vote.service.CanonicalJson;
import org.sure_vote.service.ConstraintException;
import org.sure_vote.service.Digests;
import org.sure_vote.service.HeadNotice;
import org.sure_vote.service.ImportBallot;
import org.sure_vote.service.ImportVote;
import org.sure_vote.service.LookupResult;
import org.sure_vote.service.RestoreRequest;
import org.sure_vote.service.SureVote;
import org.sure_vote.service.ValidationRequest;

public final class ApiServer implements HttpHandler {
    private final SureVote app;
    private final HttpServer server;
    private final ObjectMapper json = new ObjectMapper();
    private final WorkspaceHttp workspace;

    private ApiServer(final SureVote app, final HttpServer server) {
        this.app = app;
        this.server = server;
        this.workspace = new WorkspaceHttp(app, this.json);
    }

    public static ApiServer start(final SureVote app) {
        return start(app, 0);
    }

    public static ApiServer start(final SureVote app, final int port) {
        return start(app, port, null);
    }

    public static ApiServer start(final SureVote app, final int port, final Path staticRoot) {
        try {
            return listen(app, port, staticRoot);
        }
        catch (final IOException failure) {
            throw new IllegalStateException("HTTP server failed to start", failure);
        }
    }

    private static ApiServer listen(
        final SureVote app,
        final int port,
        final Path staticRoot
    ) throws IOException {
        final var server = HttpServer.create(new InetSocketAddress(port), 0);
        final var api = new ApiServer(app, server);
        final var pages = files(staticRoot);
        server.createContext("/", api::handle);
        mount(server, pages);
        server.start();
        return api;
    }

    private static AppFiles files(final Path staticRoot) {
        if (staticRoot == null) {
            return null;
        }
        else {
            return new AppFiles(staticRoot);
        }
    }

    private static void mount(final HttpServer server, final AppFiles pages) {
        if (pages == null) {
            return;
        }
        else {
            server.createContext("/app", pages);
        }
    }

    public int port() {
        final var address = this.server.getAddress();
        return address.getPort();
    }

    public void close() {
        this.server.stop(0);
    }

    @Override
    public void handle(final HttpExchange exchange) {
        try {
            this.route(exchange);
        }
        catch (final ConstraintException failure) {
            this.writeQuiet(exchange, 400, failure.getMessage());
        }
        catch (final JsonProcessingException failure) {
            this.writeQuiet(exchange, 400, "invalid json");
        }
        catch (final Exception failure) {
            this.writeQuiet(exchange, 500, "internal error");
        }
    }

    private void route(final HttpExchange exchange) throws IOException {
        final var uri = exchange.getRequestURI();
        final var path = uri.getPath();
        final var method = exchange.getRequestMethod();
        final var parts = this.parts(path);
        if ("GET".equals(method) && this.named(parts, "health")) {
            this.health(exchange);
        }
        else if ("POST".equals(method) && this.named(parts, "tenants")) {
            this.createTenant(exchange);
        }
        else if ("POST".equals(method) && this.named(parts, "persons")) {
            this.createPerson(exchange);
        }
        else if ("POST".equals(method) && this.named(parts, "elections")) {
            this.createElection(exchange);
        }
        else if ("POST".equals(method) && this.named(parts, "issuances")) {
            this.createIssuance(exchange);
        }
        else if ("POST".equals(method) && this.named(parts, "ballots")) {
            this.importBallot(exchange);
        }
        else if ("GET".equals(method) && this.electionItem(parts)) {
            this.showElection(exchange, parts);
        }
        else if ("POST".equals(method) && this.electionAction(parts, "definition")) {
            this.definition(exchange, parts);
        }
        else if ("POST".equals(method) && this.electionAction(parts, "activate")) {
            this.activate(exchange, parts);
        }
        else if ("POST".equals(method) && this.electionAction(parts, "close")) {
            this.closeElection(exchange, parts);
        }
        else if ("POST".equals(method) && this.issuanceVoid(parts)) {
            this.voidIssuance(exchange, parts);
        }
        else if ("GET".equals(method) && this.electionAction(parts, "ballots")) {
            this.lookup(exchange, parts, uri);
        }
        else if ("POST".equals(method) && this.electionAction(parts, "lookup")) {
            this.lookupPosted(exchange, parts);
        }
        else if ("GET".equals(method) && this.electionNested(parts, "disputes", "count")) {
            this.disputeCount(exchange, parts);
        }
        else if ("POST".equals(method) && this.electionNested(parts, "disputes", "lookup")) {
            this.disputeLookup(exchange, parts);
        }
        else if ("POST".equals(method) && this.electionAction(parts, "disputes")) {
            this.fileDispute(exchange, parts);
        }
        else if ("GET".equals(method) && this.electionAction(parts, "disputes")) {
            this.listDisputes(exchange, parts);
        }
        else if ("POST".equals(method) && this.disputeStatus(parts)) {
            this.reviewDispute(exchange, parts);
        }
        else if ("POST".equals(method) && this.named(parts, "validators")) {
            this.createValidator(exchange);
        }
        else if ("POST".equals(method) && this.named(parts, "tenant-validators")) {
            this.createTenantValidator(exchange);
        }
        else if ("GET".equals(method) && this.electionAction(parts, "heads")) {
            this.heads(exchange, parts);
        }
        else if ("GET".equals(method) && this.electionAction(parts, "validators")) {
            this.electionValidators(exchange, parts);
        }
        else if ("GET".equals(method) && this.electionAction(parts, "validations")) {
            this.listValidations(exchange, parts);
        }
        else if ("POST".equals(method) && this.electionAction(parts, "validations")) {
            this.fileValidation(exchange, parts);
        }
        else if ("POST".equals(method) && this.electionNested(parts, "restore-incidents", "hash")) {
            this.restoreHash(exchange, parts);
        }
        else if ("POST".equals(method) && this.electionAction(parts, "restore-incidents")) {
            this.fileRestoreIncident(exchange, parts);
        }
        else if ("GET".equals(method) && this.electionAction(parts, "restore-incidents")) {
            this.listRestoreIncidents(exchange, parts);
        }
        else if (this.workspace.handles(method, parts)) {
            this.workspace.handle(exchange, method, parts);
        }
        else {
            this.writeJson(exchange, 404, new ErrorResource("not found"));
        }
    }

    private void health(final HttpExchange exchange) throws IOException {
        this.writeJson(exchange, 200, new HealthResource("ok"));
    }

    private void createTenant(final HttpExchange exchange) throws IOException {
        final var actor = this.actor(exchange);
        final var body = this.read(exchange, TenantBody.class);
        final var tenant = this.tenant(body);
        final var saved = this.app.createTenant(tenant, actor);
        this.writeJson(exchange, 201, this.tenantResource(saved));
    }

    private void createPerson(final HttpExchange exchange) throws IOException {
        final var actor = this.actor(exchange);
        final var body = this.read(exchange, PersonBody.class);
        final var person = this.person(body);
        final var saved = this.app.createPerson(person, actor);
        this.writeJson(exchange, 201, this.personResource(saved));
    }

    private void createElection(final HttpExchange exchange) throws IOException {
        final var actor = this.actor(exchange);
        final var body = this.read(exchange, ElectionBody.class);
        final var election = this.election(body);
        final var saved = this.app.createElection(election, actor);
        this.writeJson(exchange, 201, this.electionResource(saved));
    }

    private void createIssuance(final HttpExchange exchange) throws IOException {
        final var actor = this.actor(exchange);
        final var body = this.read(exchange, IssuanceBody.class);
        final var issuance = this.issuance(body);
        final var saved = this.app.acceptVoter(issuance, actor);
        this.writeJson(exchange, 201, this.issuanceResource(saved));
    }

    private void importBallot(final HttpExchange exchange) throws IOException {
        final var actor = this.actor(exchange);
        final var body = this.read(exchange, BallotBody.class);
        final var request = this.importRequest(body, actor);
        final var saved = this.app.importBallot(request);
        this.writeJson(exchange, 201, this.ballotResource(saved));
    }

    private void showElection(final HttpExchange exchange, final List<String> parts) throws IOException {
        final var id = this.id(parts.get(1));
        final var election = this.app.election(id);
        this.writeJson(exchange, 200, this.electionResource(election));
    }

    private void definition(final HttpExchange exchange, final List<String> parts) throws IOException {
        final var id = this.id(parts.get(1));
        final var hash = this.app.definitionHash(id);
        this.writeJson(exchange, 200, new HashResource(hash));
    }

    private void activate(final HttpExchange exchange, final List<String> parts) throws IOException {
        final var actor = this.actor(exchange);
        final var id = this.id(parts.get(1));
        final var body = this.read(exchange, SignatureBody.class);
        final var saved = this.app.activate(id, body.signature, actor);
        this.writeJson(exchange, 200, this.electionResource(saved));
    }

    private void closeElection(final HttpExchange exchange, final List<String> parts) throws IOException {
        final var actor = this.actor(exchange);
        final var id = this.id(parts.get(1));
        final var body = this.read(exchange, CloseBody.class);
        final var signatures = this.signatures(body);
        final var saved = this.app.close(id, signatures, body.canvassSignature, actor);
        this.writeJson(exchange, 200, this.electionResource(saved));
    }

    private void voidIssuance(final HttpExchange exchange, final List<String> parts) throws IOException {
        final var actor = this.actor(exchange);
        final var id = this.id(parts.get(1));
        final var body = this.read(exchange, VoidBody.class);
        final var saved = this.app.voidIssuance(id, body.reason, actor);
        this.writeJson(exchange, 200, this.issuanceResource(saved));
    }

    private void lookup(final HttpExchange exchange, final List<String> parts, final URI uri) throws IOException {
        final var id = this.id(parts.get(1));
        final var code = this.query(uri, "code");
        final var result = this.app.lookup(id, code);
        this.writeJson(exchange, 200, this.lookupResource(result));
    }

    private void lookupPosted(final HttpExchange exchange, final List<String> parts) throws IOException {
        final var id = this.id(parts.get(1));
        final var body = this.read(exchange, LookupBody.class);
        final var result = this.app.lookup(id, body.code, body.passphrase);
        this.writeJson(exchange, 200, this.lookupResource(result));
    }

    private void fileDispute(final HttpExchange exchange, final List<String> parts) throws IOException {
        final var id = this.id(parts.get(1));
        final var body = this.read(exchange, DisputeBody.class);
        final var saved = this.app.fileDispute(id, body.code, body.passphrase, body.statement);
        this.writeJson(exchange, 201, this.disputeResource(saved));
    }

    private void disputeLookup(final HttpExchange exchange, final List<String> parts) throws IOException {
        final var id = this.id(parts.get(1));
        final var body = this.read(exchange, DisputeBody.class);
        final var rows = this.app.disputesForCode(id, body.code, body.passphrase);
        this.writeJson(exchange, 200, this.disputeList(rows));
    }

    private void disputeCount(final HttpExchange exchange, final List<String> parts) throws IOException {
        final var id = this.id(parts.get(1));
        final var count = this.app.disputeCount(id);
        final var resource = new DisputeCountResource();
        resource.setCount(count);
        this.writeJson(exchange, 200, resource);
    }

    private void listDisputes(final HttpExchange exchange, final List<String> parts) throws IOException {
        final var actor = this.actor(exchange);
        final var id = this.id(parts.get(1));
        final var rows = this.app.listDisputes(id, actor);
        this.writeJson(exchange, 200, this.disputeList(rows));
    }

    private void reviewDispute(final HttpExchange exchange, final List<String> parts) throws IOException {
        final var actor = this.actor(exchange);
        final var id = this.id(parts.get(1));
        final var body = this.read(exchange, DisputeStatusBody.class);
        final var status = this.wire(DisputeStatus.values(), body.status, "dispute status");
        final var saved = this.app.setDisputeStatus(id, status, actor);
        this.writeJson(exchange, 200, this.disputeResource(saved));
    }

    private void createValidator(final HttpExchange exchange) throws IOException {
        final var actor = this.actor(exchange);
        final var body = this.read(exchange, ValidatorBody.class);
        final var draft = this.validatorDraft(body);
        final var saved = this.app.registerValidator(draft, actor);
        this.writeJson(exchange, 201, this.validatorResource(saved));
    }

    private void createTenantValidator(final HttpExchange exchange) throws IOException {
        final var actor = this.actor(exchange);
        final var body = this.read(exchange, TenantValidatorBody.class);
        final var link = this.tenantValidator(body);
        final var saved = this.app.registerTenantValidator(link, actor);
        this.writeJson(exchange, 201, this.tenantValidatorResource(saved));
    }

    private void heads(final HttpExchange exchange, final List<String> parts) throws IOException {
        final var id = this.id(parts.get(1));
        final var notices = this.app.heads(id);
        this.writeJson(exchange, 200, this.headList(notices));
    }

    private void electionValidators(final HttpExchange exchange, final List<String> parts) throws IOException {
        final var id = this.id(parts.get(1));
        final var rows = this.app.electionValidators(id);
        this.writeJson(exchange, 200, this.validatorList(rows));
    }

    private void listValidations(final HttpExchange exchange, final List<String> parts) throws IOException {
        final var id = this.id(parts.get(1));
        final var rows = this.app.validationResults(id);
        this.writeJson(exchange, 200, this.validationList(rows));
    }

    private void fileValidation(final HttpExchange exchange, final List<String> parts) throws IOException {
        final var actor = this.actor(exchange);
        final var id = this.id(parts.get(1));
        final var body = this.read(exchange, ValidationBody.class);
        final var request = this.validationRequest(body);
        final var saved = this.app.fileValidation(id, request, actor);
        this.writeJson(exchange, 201, this.validationResource(saved));
    }

    private void restoreHash(final HttpExchange exchange, final List<String> parts) throws IOException {
        final var id = this.id(parts.get(1));
        final var body = this.read(exchange, RestoreBody.class);
        final var when = this.instant(body.timestamp);
        final var payload = this.app.restoreIncidentPayload(id, body.reason, when);
        this.writeJson(exchange, 200, new HashResource(Digests.sha256Hex(payload)));
    }

    private void fileRestoreIncident(final HttpExchange exchange, final List<String> parts) throws IOException {
        final var actor = this.actor(exchange);
        final var id = this.id(parts.get(1));
        final var body = this.read(exchange, RestoreBody.class);
        final var when = this.instant(body.timestamp);
        final var request = new RestoreRequest(body.reason, when, body.signature);
        final var saved = this.app.fileRestoreIncident(id, request, actor);
        this.writeJson(exchange, 201, this.restoreResource(saved));
    }

    private void listRestoreIncidents(final HttpExchange exchange, final List<String> parts) throws IOException {
        final var id = this.id(parts.get(1));
        final var rows = this.app.restoreIncidents(id);
        final var resources = new ArrayList<RestoreIncidentResource>();
        final var list = new RestoreIncidentListResource();
        for (final var row : rows) {
            resources.add(this.restoreResource(row));
        }
        list.setIncidents(resources);
        this.writeJson(exchange, 200, list);
    }

    private RestoreIncidentResource restoreResource(final RestoreIncident row) {
        final var resource = new RestoreIncidentResource();
        resource.setId(row.getId());
        resource.setTenantId(row.getTenantId());
        resource.setElectionId(row.getElectionId());
        resource.setPayload(row.getPayload());
        resource.setHash(row.getHash());
        resource.setSignature(row.getSignature());
        resource.setCreatedAt(this.time(row.getCreatedAt()));
        return resource;
    }

    private boolean named(final List<String> parts, final String name) {
        if (parts.size() != 1) {
            return false;
        }
        else {
            return name.equals(parts.getFirst());
        }
    }

    private boolean electionItem(final List<String> parts) {
        if (parts.size() != 2) {
            return false;
        }
        else {
            return "elections".equals(parts.getFirst());
        }
    }

    private boolean electionNested(final List<String> parts, final String action, final String leaf) {
        if (parts.size() != 4) {
            return false;
        }
        else {
            return this.nestedMatch(parts, action, leaf);
        }
    }

    private boolean nestedMatch(final List<String> parts, final String action, final String leaf) {
        final var root = parts.get(0);
        final var name = parts.get(2);
        final var child = parts.get(3);
        return "elections".equals(root) && action.equals(name) && leaf.equals(child);
    }

    private boolean disputeStatus(final List<String> parts) {
        if (parts.size() != 3) {
            return false;
        }
        else {
            final var root = parts.get(0);
            final var action = parts.get(2);
            return "disputes".equals(root) && "status".equals(action);
        }
    }

    private boolean electionAction(final List<String> parts, final String action) {
        if (parts.size() != 3) {
            return false;
        }
        else {
            final var root = parts.get(0);
            final var name = parts.get(2);
            return "elections".equals(root) && action.equals(name);
        }
    }

    private boolean issuanceVoid(final List<String> parts) {
        if (parts.size() != 3) {
            return false;
        }
        else {
            final var root = parts.get(0);
            final var action = parts.get(2);
            return "issuances".equals(root) && "void".equals(action);
        }
    }

    private List<String> parts(final String path) {
        final var split = path.split("/");
        final var parts = new ArrayList<String>();
        for (final var part : split) {
            this.addPart(parts, part);
        }
        return parts;
    }

    private void addPart(final List<String> parts, final String part) {
        if (!part.isEmpty()) {
            parts.add(part);
        }
    }

    private Tenant tenant(final TenantBody body) {
        final var tenant = new Tenant();
        tenant.setName(body.name);
        tenant.setKind(this.wire(TenantKind.values(), body.kind, "tenant kind"));
        tenant.setResidencyRegion(body.residencyRegion);
        tenant.setStatus(this.wire(TenantStatus.values(), body.status, "tenant status"));
        return tenant;
    }

    private Person person(final PersonBody body) {
        final var person = new Person();
        person.setTenantId(body.tenantId);
        person.setFirstName(body.firstName);
        person.setLastName(body.lastName);
        person.setSsn(body.ssn);
        person.setVoterId(body.voterId);
        return person;
    }

    private Election election(final ElectionBody body) {
        final var election = new Election();
        election.setTenantId(body.tenantId);
        election.setLocalityId(body.localityId);
        election.setName(body.name);
        election.setVotingStart(this.instant(body.votingStart));
        election.setVotingEnd(this.instant(body.votingEnd));
        election.setOperatorPublicKey(body.operatorPublicKey);
        election.setImportPublicKey(body.importPublicKey);
        election.setViewingPublicKey(body.viewingPublicKey);
        return election;
    }

    private BallotIssuance issuance(final IssuanceBody body) {
        final var issuance = new BallotIssuance();
        issuance.setTenantId(body.tenantId);
        issuance.setPersonId(body.personId);
        issuance.setElectionId(body.electionId);
        issuance.setRecordingPlaceId(body.recordingPlaceId);
        return issuance;
    }

    private ImportBallot importRequest(final BallotBody body, final UUID actor) {
        final var type = this.wire(BallotType.values(), body.type, "ballot type");
        final var votes = this.votes(body.votes);
        return new ImportBallot(
            body.tenantId,
            actor,
            body.electionId,
            body.votingPrecinctId,
            type,
            body.recordingDeviceId,
            body.recordingPlaceId,
            body.recordedBy,
            this.instant(body.timestamp),
            body.voterAccessHash,
            body.signature,
            votes,
            body.personId
        );
    }

    private List<ImportVote> votes(final List<VoteBody> bodies) {
        if (bodies == null) {
            return null;
        }
        else {
            final var votes = new ArrayList<ImportVote>();
            for (final var body : bodies) {
                votes.add(this.vote(body));
            }
            return votes;
        }
    }

    private ImportVote vote(final VoteBody body) {
        return new ImportVote(
            body.raceCandidateId,
            body.value,
            body.signature,
            this.instant(body.entryTimestamp),
            body.nonce,
            body.entrySignature
        );
    }

    private Map<UUID, String> signatures(final CloseBody body) {
        final var signatures = new LinkedHashMap<UUID, String>();
        this.addSignatures(signatures, body);
        return signatures;
    }

    private void addSignatures(final Map<UUID, String> signatures, final CloseBody body) {
        if (body.chains == null) {
            return;
        }
        else {
            for (final var chain : body.chains) {
                signatures.put(chain.raceId, chain.signature);
            }
        }
    }

    private TenantResource tenantResource(final Tenant tenant) {
        final var resource = new TenantResource();
        final var kind = tenant.getKind();
        final var status = tenant.getStatus();
        resource.setId(tenant.getId());
        resource.setName(tenant.getName());
        resource.setKind(kind.wire());
        resource.setResidencyRegion(tenant.getResidencyRegion());
        resource.setStatus(status.wire());
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

    private IssuanceResource issuanceResource(final BallotIssuance issuance) {
        final var resource = new IssuanceResource();
        final var status = issuance.getStatus();
        resource.setId(issuance.getId());
        resource.setTenantId(issuance.getTenantId());
        resource.setPersonId(issuance.getPersonId());
        resource.setElectionId(issuance.getElectionId());
        resource.setRecordingPlaceId(issuance.getRecordingPlaceId());
        resource.setStatus(status.wire());
        return resource;
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

    private DisputeResource disputeResource(final BallotDispute dispute) {
        final var resource = new DisputeResource();
        final var status = dispute.getStatus();
        resource.setId(dispute.getId());
        resource.setBallotId(dispute.getBallotId());
        resource.setStatus(status.wire());
        resource.setStatement(dispute.getStatement());
        resource.setFiledAt(CanonicalJson.timestampText(dispute.getFiledAt()));
        return resource;
    }

    private DisputeListResource disputeList(final List<BallotDispute> rows) {
        final var resources = new ArrayList<DisputeResource>();
        final var resource = new DisputeListResource();
        this.addDisputes(resources, rows);
        resource.setDisputes(resources);
        return resource;
    }

    private void addDisputes(final List<DisputeResource> resources, final List<BallotDispute> rows) {
        for (final var dispute : rows) {
            resources.add(this.disputeResource(dispute));
        }
    }

    private LookupResource lookupResource(final LookupResult result) {
        final var resource = new LookupResource();
        final var votes = new ArrayList<VoteResource>();
        this.addVotes(votes, result.votes());
        resource.setBallot(this.ballotResource(result.ballot()));
        resource.setVotes(votes);
        return resource;
    }

    private void addVotes(final List<VoteResource> resources, final List<Vote> votes) {
        for (final var vote : votes) {
            resources.add(this.voteResource(vote));
        }
    }

    private VoteResource voteResource(final Vote vote) {
        final var resource = new VoteResource();
        resource.setId(vote.getId());
        resource.setRaceCandidateId(vote.getRaceCandidateId());
        resource.setValue(vote.getValue());
        resource.setHash(vote.getHash());
        return resource;
    }

    private VoteChainValidator validatorDraft(final ValidatorBody body) {
        final var draft = new VoteChainValidator();
        draft.setName(body.name);
        draft.setDescription(body.description);
        draft.setPublicKey(body.publicKey);
        return draft;
    }

    private TenantValidator tenantValidator(final TenantValidatorBody body) {
        final var link = new TenantValidator();
        link.setTenantId(body.tenantId);
        link.setValidatorId(body.validatorId);
        return link;
    }

    private ValidationRequest validationRequest(final ValidationBody body) {
        final var result = this.wire(ValidationResultStatus.values(), body.result, "validation result");
        final var when = this.instant(body.dateValidated);
        return new ValidationRequest(
            body.voteChainId,
            body.validatorId,
            body.validatedThroughHash,
            body.length,
            result,
            when,
            body.method,
            body.notes,
            body.evidenceHash,
            body.signature
        );
    }

    private ValidatorResource validatorResource(final VoteChainValidator validator) {
        final var resource = new ValidatorResource();
        resource.setId(validator.getId());
        resource.setName(validator.getName());
        resource.setDescription(validator.getDescription());
        resource.setPublicKey(validator.getPublicKey());
        return resource;
    }

    private TenantValidatorResource tenantValidatorResource(final TenantValidator link) {
        final var resource = new TenantValidatorResource();
        resource.setId(link.getId());
        resource.setTenantId(link.getTenantId());
        resource.setValidatorId(link.getValidatorId());
        return resource;
    }

    private HeadListResource headList(final List<HeadNotice> notices) {
        final var resources = new ArrayList<HeadResource>();
        final var list = new HeadListResource();
        this.addHeads(resources, notices);
        list.setHeads(resources);
        return list;
    }

    private void addHeads(final List<HeadResource> resources, final List<HeadNotice> notices) {
        for (final var notice : notices) {
            resources.add(this.headResource(notice));
        }
    }

    private HeadResource headResource(final HeadNotice notice) {
        final var resource = new HeadResource();
        resource.setElectionId(notice.electionId());
        resource.setRaceId(notice.raceId());
        resource.setLength(notice.length());
        resource.setHeadHash(notice.headHash());
        resource.setObservedAt(this.time(notice.observedAt()));
        resource.setSignature(notice.signature());
        resource.setCanvassHash(notice.canvassHash());
        resource.setWitnessGap(notice.witnessGap());
        return resource;
    }

    private ValidatorListResource validatorList(final List<VoteChainValidator> rows) {
        final var resources = new ArrayList<ValidatorResource>();
        final var list = new ValidatorListResource();
        this.addValidators(resources, rows);
        list.setValidators(resources);
        return list;
    }

    private void addValidators(final List<ValidatorResource> resources, final List<VoteChainValidator> rows) {
        for (final var row : rows) {
            resources.add(this.validatorResource(row));
        }
    }

    private ValidationListResource validationList(final List<VoteChainValidationResult> rows) {
        final var resources = new ArrayList<ValidationResource>();
        final var list = new ValidationListResource();
        this.addValidations(resources, rows);
        list.setValidations(resources);
        return list;
    }

    private void addValidations(
        final List<ValidationResource> resources,
        final List<VoteChainValidationResult> rows
    ) {
        for (final var row : rows) {
            resources.add(this.validationResource(row));
        }
    }

    private ValidationResource validationResource(final VoteChainValidationResult row) {
        final var resource = new ValidationResource();
        final var result = row.getResult();
        resource.setId(row.getId());
        resource.setTenantId(row.getTenantId());
        resource.setVoteChainId(row.getVoteChainId());
        resource.setValidatorId(row.getValidatorId());
        resource.setDateValidated(this.time(row.getDateValidated()));
        resource.setValidatedThroughHash(row.getValidatedThroughHash());
        resource.setLength(row.getLength());
        resource.setMethod(row.getMethod());
        resource.setResult(result.wire());
        resource.setNotes(row.getNotes());
        resource.setEvidenceHash(row.getEvidenceHash());
        resource.setPayload(row.getPayload());
        resource.setHash(row.getHash());
        resource.setSignature(row.getSignature());
        return resource;
    }

    private String time(final Instant instant) {
        if (instant == null) {
            return null;
        }
        else {
            return CanonicalJson.timestampText(instant);
        }
    }

    private <T> T read(final HttpExchange exchange, final Class<T> type) throws IOException {
        final var input = exchange.getRequestBody();
        final var bytes = input.readAllBytes();
        return this.json.readValue(bytes, type);
    }

    private void writeJson(final HttpExchange exchange, final int status, final Object body) throws IOException {
        final var bytes = this.json.writeValueAsBytes(body);
        this.send(exchange, status, bytes);
    }

    private void send(final HttpExchange exchange, final int status, final byte[] bytes) throws IOException {
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

    private void writeQuiet(final HttpExchange exchange, final int status, final String message) {
        try {
            this.writeJson(exchange, status, new ErrorResource(message));
        }
        catch (final IOException failure) {
            exchange.close();
        }
    }

    private UUID actor(final HttpExchange exchange) {
        final var headers = exchange.getRequestHeaders();
        final var values = headers.get("SureVote-Actor-Id");
        if (values == null || values.isEmpty()) {
            throw new ConstraintException("SureVote-Actor-Id is required");
        }
        else {
            final var text = values.getFirst();
            return this.id(text);
        }
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

    private String query(final URI uri, final String key) {
        final var raw = uri.getRawQuery();
        if (raw == null) {
            return null;
        }
        else {
            final var pairs = raw.split("&");
            return this.findQuery(pairs, key);
        }
    }

    private String findQuery(final String[] pairs, final String key) {
        for (final var pair : pairs) {
            final var found = this.queryValue(pair, key);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private String queryValue(final String pair, final String key) {
        final var pieces = pair.split("=", 2);
        final var matches = pieces.length == 2 && key.equals(this.decode(pieces[0]));
        if (matches) {
            return this.decode(pieces[1]);
        }
        else {
            return null;
        }
    }

    private String decode(final String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }

    public static final class TenantBody {
        public String name;
        public String kind;
        public String residencyRegion;
        public String status;
    }

    public static final class PersonBody {
        public UUID tenantId;
        public String firstName;
        public String lastName;
        public String ssn;
        public String voterId;
    }

    public static final class ElectionBody {
        public UUID tenantId;
        public UUID localityId;
        public String name;
        public String votingStart;
        public String votingEnd;
        public String operatorPublicKey;
        public String importPublicKey;
        public String viewingPublicKey;
    }

    public static final class IssuanceBody {
        public UUID tenantId;
        public UUID personId;
        public UUID electionId;
        public UUID recordingPlaceId;
    }

    public static final class BallotBody {
        public UUID tenantId;
        public UUID electionId;
        public UUID votingPrecinctId;
        public String type;
        public UUID recordingDeviceId;
        public UUID recordingPlaceId;
        public UUID recordedBy;
        public String timestamp;
        public String voterAccessHash;
        public String signature;
        public List<VoteBody> votes;
        public UUID personId;
    }

    public static final class LookupBody {
        public String code;
        public String passphrase;
    }

    public static final class DisputeBody {
        public String code;
        public String passphrase;
        public String statement;
    }

    public static final class DisputeStatusBody {
        public String status;
    }

    public static final class VoteBody {
        public UUID raceCandidateId;
        public String value;
        public String signature;
        public String entryTimestamp;
        public String nonce;
        public String entrySignature;
    }

    public static final class ValidatorBody {
        public String name;
        public String description;
        public String publicKey;
    }

    public static final class TenantValidatorBody {
        public UUID tenantId;
        public UUID validatorId;
    }

    public static final class ValidationBody {
        public UUID voteChainId;
        public UUID validatorId;
        public String validatedThroughHash;
        public int length;
        public String result;
        public String dateValidated;
        public String method;
        public String notes;
        public String evidenceHash;
        public String signature;
    }

    public static final class RestoreBody {
        public String reason;
        public String timestamp;
        public String signature;
    }

    public static final class SignatureBody {
        public String signature;
    }

    public static final class VoidBody {
        public String reason;
    }

    public static final class CloseBody {
        public List<ChainBody> chains;
        public String canvassSignature;
    }

    public static final class ChainBody {
        public UUID raceId;
        public String signature;
    }
}
