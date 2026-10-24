package org.sure_vote.api;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
import org.sure_vote.entity.BallotIssuance;
import org.sure_vote.entity.BallotType;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.Person;
import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.TenantKind;
import org.sure_vote.entity.TenantStatus;
import org.sure_vote.entity.Vote;
import org.sure_vote.entity.WireValue;
import org.sure_vote.entity.Wires;
import org.sure_vote.resource.BallotResource;
import org.sure_vote.resource.ElectionResource;
import org.sure_vote.resource.ErrorResource;
import org.sure_vote.resource.HashResource;
import org.sure_vote.resource.HealthResource;
import org.sure_vote.resource.IssuanceResource;
import org.sure_vote.resource.LookupResource;
import org.sure_vote.resource.PersonResource;
import org.sure_vote.resource.TenantResource;
import org.sure_vote.resource.VoteResource;
import org.sure_vote.service.CanonicalJson;
import org.sure_vote.service.ConstraintException;
import org.sure_vote.service.ImportBallot;
import org.sure_vote.service.ImportVote;
import org.sure_vote.service.LookupResult;
import org.sure_vote.service.SureVote;

public final class ApiServer implements HttpHandler {
    private final SureVote app;
    private final HttpServer server;
    private final ObjectMapper json = new ObjectMapper();

    private ApiServer(final SureVote app, final HttpServer server) {
        this.app = app;
        this.server = server;
    }

    public static ApiServer start(final SureVote app) {
        return start(app, 0);
    }

    public static ApiServer start(final SureVote app, final int port) {
        try {
            final var server = HttpServer.create(new InetSocketAddress(port), 0);
            final var api = new ApiServer(app, server);
            server.createContext("/", api::handle);
            server.start();
            return api;
        }
        catch (final IOException failure) {
            throw new IllegalStateException("HTTP server failed to start", failure);
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
        final var saved = this.app.close(id, signatures, actor);
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
            votes
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
        resource.setId(election.getId());
        resource.setTenantId(election.getTenantId());
        resource.setName(election.getName());
        resource.setStatus(status.wire());
        resource.setVotingStart(CanonicalJson.timestampText(election.getVotingStart()));
        resource.setVotingEnd(CanonicalJson.timestampText(election.getVotingEnd()));
        resource.setHash(election.getHash());
        resource.setSignature(election.getSignature());
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
        final var values = headers.get("X-Actor-Id");
        if (values == null || values.isEmpty()) {
            throw new ConstraintException("X-Actor-Id is required");
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
        catch (final DateTimeParseException failure) {
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
    }

    public static final class VoteBody {
        public UUID raceCandidateId;
        public String value;
        public String signature;
        public String entryTimestamp;
        public String nonce;
        public String entrySignature;
    }

    public static final class SignatureBody {
        public String signature;
    }

    public static final class VoidBody {
        public String reason;
    }

    public static final class CloseBody {
        public List<ChainBody> chains;
    }

    public static final class ChainBody {
        public UUID raceId;
        public String signature;
    }
}
