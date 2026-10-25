package org.sure_vote.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.sure_vote.api.ApiServer;
import org.sure_vote.entity.ElectionStatus;
import org.sure_vote.entity.IssuanceStatus;
import org.sure_vote.entity.VoteChain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpElectionTest {
    private HttpClient client;
    private ObjectMapper json;
    private String base;
    private String actorId;

    @Test
    void definitionAndActivate() throws Exception {
        final var world = SampleElection.upcomingFptp();
        this.use(world, () -> this.activate(world));
    }

    @Test
    void issuanceAndVoid() throws Exception {
        final var world = SampleElection.fptp();
        this.use(world, () -> this.voidIssuance(world));
    }

    @Test
    void importLookupAndClose() throws Exception {
        final var world = SampleElection.fptp();
        this.use(world, () -> this.record(world));
    }

    @Test
    void disputeCountOmitsTheStatement() throws Exception {
        final var world = SampleElection.fptp();
        this.use(world, () -> this.dispute(world));
    }

    @Test
    void headsStayPrivateUntilTheElectionIsActive() throws Exception {
        final var world = SampleElection.upcomingFptp();
        this.use(world, () -> this.hiddenHeads(world));
    }

    @Test
    void aValidatorSignsThePublishedHead() throws Exception {
        final var world = SampleElection.fptp();
        this.use(world, () -> this.witness(world));
    }

    private void activate(final SampleElection world) throws Exception {
        final var path = "/elections/" + world.electionId;
        final var defined = this.send("POST", path + "/definition", "{}");
        final var definedBody = defined.body();
        final var hash = this.text(definedBody, "hash");
        final var raw = Digests.unhex(hash);
        final var seed = world.operator.seed();
        final var signature = Signatures.sign(seed, raw);
        final var activated = this.send("POST", path + "/activate", this.signatureJson(signature));
        final var shown = this.send("GET", path, null);
        final var activatedBody = activated.body();
        final var shownBody = shown.body();
        final var status = this.text(activatedBody, "status");
        final var activatedHash = this.text(activatedBody, "hash");
        final var name = this.text(shownBody, "name");
        final var start = this.text(shownBody, "votingStart");
        final var shownHash = this.text(shownBody, "hash");
        final var demonstration = this.text(shownBody, "demonstration");
        assertEquals(200, defined.statusCode());
        assertEquals(64, hash.length());
        assertEquals(200, activated.statusCode());
        assertEquals("active", status);
        assertEquals(hash, activatedHash);
        assertEquals(signature, this.text(activatedBody, "signature"));
        assertEquals(200, shown.statusCode());
        assertEquals("General", name);
        assertEquals("2026-11-03T00:00:00.000Z", start);
        assertEquals(hash, shownHash);
        assertEquals("false", demonstration);
    }

    @Test
    void aProductionTenantRejectsHostKeys() throws Exception {
        final var world = SampleElection.fptp();
        this.use(world, () -> this.rejectHostKeys(world));
    }

    @Test
    void workspaceListsALocality() throws Exception {
        final var world = SampleElection.showcase();
        this.use(world, () -> this.listLocality(world));
    }

    private void rejectHostKeys(final SampleElection world) throws Exception {
        final var body = "{\"tenantId\":\"" + world.tenantId
            + "\",\"name\":\"Nope\",\"votingStart\":\"2026-11-03T00:00:00Z\""
            + ",\"votingEnd\":\"2026-11-04T00:00:00Z\"}";
        final var created = this.send("POST", "/showcase/elections", body);
        final var createdBody = created.body();
        final var message = this.text(createdBody, "error");
        assertEquals(400, created.statusCode());
        assertEquals("only a showcase tenant keeps host keys", message);
    }

    private void listLocality(final SampleElection world) throws Exception {
        final var body = "{\"tenantId\":\"" + world.tenantId + "\",\"name\":\"Town\",\"type\":\"city\"}";
        final var created = this.send("POST", "/localities", body);
        final var path = "/tenants/" + world.tenantId + "/workspace";
        final var listed = this.send("GET", path, null);
        final var createdBody = created.body();
        final var listedBody = listed.body();
        final var name = this.text(createdBody, "name");
        assertEquals(201, created.statusCode());
        assertEquals("Town", name);
        assertEquals(200, listed.statusCode());
        assertTrue(listedBody.contains("\"name\":\"Town\""));
    }

    @Test
    void aShowcaseWorkspaceCastsAndCloses() throws Exception {
        final var world = SampleElection.showcase();
        this.use(world, () -> this.castShowcase(world));
    }

    private void castShowcase(final SampleElection world) throws Exception {
        final var locality = this.idFrom("/localities", this.localityJson(world));
        final var address = this.idFrom("/addresses", this.addressJson(world, locality));
        final var precinct = this.idFrom("/precincts", this.precinctJson(world, address));
        final var district = this.idFrom("/districts", this.districtJson(world, locality));
        final var linked = this.idFrom("/precinct-districts", this.linkJson(world, precinct, district));
        final var seat = this.idFrom("/seats", this.seatJson(world, locality));
        final var term = this.idFrom("/terms", this.termJson(world, seat));
        final var election = this.idFrom("/showcase/elections", this.showcaseElectionJson(world, locality));
        final var race = this.idFrom("/races", this.raceJson(world, election, term));
        final var ada = this.idFrom("/persons", this.personJson(world, "Ada", "One"));
        final var voter = this.idFrom("/persons", this.personJson(world, "Cam", "Voter"));
        final var candidate = this.idFrom("/candidates", this.candidateJson(world, ada));
        final var slot = this.idFrom("/race-candidates", this.slotJson(world, race, candidate));
        final var style = this.idFrom("/ballot-styles", this.styleJson(world, election, precinct, race));
        final var place = this.idFrom("/places", this.placeJson(world, locality, address));
        final var device = this.idFrom("/showcase/devices", this.showcaseDeviceJson(world));
        final var owned = this.idFrom("/devices", this.deviceJson(world));
        final var assignment = this.idFrom("/precinct-assignments", this.assignmentJson(world, election, precinct));
        final var active = this.saved("/showcase/elections/" + election + "/activate", "{}");
        final var cast = this.demoCast(election, precinct, device, place, voter, slot);
        final var closed = this.saved("/showcase/elections/" + election + "/close", "{}");
        final var listed = this.workspaceBody(world);
        final var code = this.text(cast, "code");
        final var payload = this.nested(cast, "ballot", "payload");
        final var value = this.voteValue(cast);
        assertEquals(43, code.length());
        assertEquals("Y", value);
        assertFalse(payload.contains(code));
        assertFalse(listed.contains(code));
        assertTrue(active.contains("\"demonstration\":true"));
        assertTrue(active.contains("\"status\":\"active\""));
        assertTrue(closed.contains("\"status\":\"historical\""));
        assertTrue(listed.contains("\"name\":\"Town\""));
        assertTrue(listed.contains("\"name\":\"Mayor\""));
        assertTrue(listed.contains("\"name\":\"Machine 1\""));
        assertTrue(listed.contains("\"name\":\"Machine 2\""));
        assertTrue(listed.contains("Ward 1"));
        assertFalse(linked.isBlank());
        assertFalse(style.isBlank());
        assertFalse(owned.isBlank());
        assertFalse(assignment.isBlank());
    }

    private void hiddenHeads(final SampleElection world) throws Exception {
        final var response = this.send("GET", "/elections/" + world.electionId + "/heads", null);
        final var body = response.body();
        assertEquals(400, response.statusCode());
        assertTrue(body.contains("election is not public yet"));
    }

    private void witness(final SampleElection world) throws Exception {
        final var chain = this.chain(world);
        final var path = "/elections/" + world.electionId;
        final var heads = this.send("GET", path + "/heads", null);
        final var headBody = heads.body();
        final var filed = this.saved(path + "/validations", this.validationJson(world, chain));
        final var listed = this.send("GET", path + "/validations", null);
        final var validators = this.send("GET", path + "/validators", null);
        final var listBody = listed.body();
        final var validatorBody = validators.body();
        final var publicKey = world.validatorKey.publicKey();
        assertEquals(200, heads.statusCode());
        assertTrue(headBody.contains("\"length\":0"));
        assertTrue(headBody.contains("\"witnessGap\":false"));
        assertTrue(headBody.contains("\"signature\":null"));
        assertTrue(headBody.contains(chain.getHash()));
        assertTrue(filed.contains("\"result\":\"valid\""));
        assertEquals(200, listed.statusCode());
        assertTrue(listBody.contains("\"result\":\"valid\""));
        assertEquals(200, validators.statusCode());
        assertTrue(validatorBody.contains("Outside witness"));
        assertTrue(validatorBody.contains(publicKey));
    }

    private String validationJson(final SampleElection world, final VoteChain chain) {
        final var when = Instant.parse("2026-11-05T12:00:00.000Z");
        final var payload = Payloads.validation(
            chain.getId(),
            world.validatorId,
            chain.getHash(),
            chain.getLength(),
            "valid",
            when,
            null,
            null
        );
        final var digest = Digests.sha256Hex(payload);
        final var raw = Digests.unhex(digest);
        final var seed = world.validatorKey.seed();
        final var signature = Signatures.sign(seed, raw);
        return "{\"voteChainId\":\"" + chain.getId()
            + "\",\"validatorId\":\"" + world.validatorId
            + "\",\"validatedThroughHash\":\"" + chain.getHash()
            + "\",\"length\":" + chain.getLength()
            + ",\"result\":\"valid\",\"dateValidated\":\"2026-11-05T12:00:00.000Z\""
            + ",\"method\":\"crypto\",\"signature\":\"" + signature + "\"}";
    }

    private VoteChain chain(final SampleElection world) {
        final var rows = world.repository.list(VoteChain.class);
        for (final var row : rows) {
            if (world.raceId.equals(row.getRaceId())) {
                return row;
            }
        }
        throw new IllegalStateException("missing chain");
    }

    private String demoCast(
        final String election,
        final String precinct,
        final String device,
        final String place,
        final String voter,
        final String slot
    ) throws Exception {
        final var body = "{\"votingPrecinctId\":\"" + precinct
            + "\",\"recordingDeviceId\":\"" + device
            + "\",\"recordingPlaceId\":\"" + place
            + "\",\"personId\":\"" + voter
            + "\",\"selections\":[{\"raceCandidateId\":\"" + slot + "\",\"value\":\"Y\"}]}";
        return this.saved("/showcase/elections/" + election + "/cast", body);
    }

    private String workspaceBody(final SampleElection world) throws Exception {
        final var path = "/tenants/" + world.tenantId + "/workspace";
        final var response = this.send("GET", path, null);
        final var body = response.body();
        final var status = response.statusCode();
        if (status == 200) {
            return body;
        }
        else {
            throw new IllegalStateException(body);
        }
    }

    private String idFrom(final String path, final String body) throws Exception {
        final var saved = this.saved(path, body);
        return this.text(saved, "id");
    }

    private String saved(final String path, final String body) throws Exception {
        final var response = this.send("POST", path, body);
        final var responseBody = response.body();
        final var status = response.statusCode();
        if (status == 200 || status == 201) {
            return responseBody;
        }
        else {
            throw new IllegalStateException(status + " " + responseBody);
        }
    }

    private String localityJson(final SampleElection world) {
        return "{\"tenantId\":\"" + world.tenantId + "\",\"name\":\"Town\",\"type\":\"city\"}";
    }

    private String addressJson(final SampleElection world, final String locality) {
        return "{\"tenantId\":\"" + world.tenantId
            + "\",\"localityId\":\"" + locality
            + "\",\"street1\":\"1 Main\",\"postalCode\":\"00000\"}";
    }

    private String precinctJson(final SampleElection world, final String address) {
        return "{\"tenantId\":\"" + world.tenantId
            + "\",\"name\":\"Precinct 1\",\"addressId\":\"" + address + "\"}";
    }

    private String districtJson(final SampleElection world, final String locality) {
        return "{\"tenantId\":\"" + world.tenantId
            + "\",\"localityId\":\"" + locality
            + "\",\"name\":\"Ward 1\",\"type\":\"city-council\"}";
    }

    private String linkJson(final SampleElection world, final String precinct, final String district) {
        return "{\"tenantId\":\"" + world.tenantId
            + "\",\"votingPrecinctId\":\"" + precinct
            + "\",\"votingDistrictId\":\"" + district
            + "\",\"districtType\":\"city-council\"}";
    }

    private String seatJson(final SampleElection world, final String locality) {
        return "{\"tenantId\":\"" + world.tenantId
            + "\",\"localityId\":\"" + locality
            + "\",\"name\":\"Mayor\",\"jurisdiction\":\"local\",\"type\":\"mayor\""
            + ",\"appointments\":1,\"termLength\":4}";
    }

    private String termJson(final SampleElection world, final String seat) {
        return "{\"tenantId\":\"" + world.tenantId + "\",\"seatId\":\"" + seat + "\"}";
    }

    private String showcaseElectionJson(final SampleElection world, final String locality) {
        final var now = Instant.now();
        final var start = now.minus(Duration.ofHours(1));
        final var end = now.plus(Duration.ofDays(1));
        return "{\"tenantId\":\"" + world.tenantId
            + "\",\"localityId\":\"" + locality
            + "\",\"name\":\"General\",\"votingStart\":\"" + start
            + "\",\"votingEnd\":\"" + end + "\"}";
    }

    private String raceJson(final SampleElection world, final String election, final String term) {
        return "{\"tenantId\":\"" + world.tenantId
            + "\",\"electionId\":\"" + election
            + "\",\"termId\":\"" + term
            + "\",\"name\":\"Mayor\",\"type\":\"general\",\"method\":\"first-past-the-post\"}";
    }

    private String personJson(final SampleElection world, final String first, final String last) {
        return "{\"tenantId\":\"" + world.tenantId
            + "\",\"firstName\":\"" + first
            + "\",\"lastName\":\"" + last + "\"}";
    }

    private String candidateJson(final SampleElection world, final String person) {
        return "{\"tenantId\":\"" + world.tenantId + "\",\"personId\":\"" + person + "\"}";
    }

    private String slotJson(final SampleElection world, final String race, final String candidate) {
        return "{\"tenantId\":\"" + world.tenantId
            + "\",\"raceId\":\"" + race
            + "\",\"candidateId\":\"" + candidate
            + "\",\"writeIn\":false}";
    }

    private String styleJson(
        final SampleElection world,
        final String election,
        final String precinct,
        final String race
    ) {
        return "{\"tenantId\":\"" + world.tenantId
            + "\",\"electionId\":\"" + election
            + "\",\"votingPrecinctId\":\"" + precinct
            + "\",\"raceId\":\"" + race + "\"}";
    }

    private String placeJson(final SampleElection world, final String locality, final String address) {
        return "{\"tenantId\":\"" + world.tenantId
            + "\",\"localityId\":\"" + locality
            + "\",\"addressId\":\"" + address
            + "\",\"name\":\"Town Hall\",\"kind\":\"polling-place\"}";
    }

    private String showcaseDeviceJson(final SampleElection world) {
        return "{\"tenantId\":\"" + world.tenantId
            + "\",\"name\":\"Machine 1\",\"kind\":\"polling-place-machine\"}";
    }

    private String deviceJson(final SampleElection world) {
        final var key = Signatures.generate();
        final var publicKey = key.publicKey();
        return "{\"tenantId\":\"" + world.tenantId
            + "\",\"name\":\"Machine 2\",\"kind\":\"polling-place-machine\""
            + ",\"publicKey\":\"" + publicKey
            + "\",\"status\":\"active\"}";
    }

    private String assignmentJson(final SampleElection world, final String election, final String precinct) {
        return "{\"tenantId\":\"" + world.tenantId
            + "\",\"electionId\":\"" + election
            + "\",\"votingPrecinctId\":\"" + precinct
            + "\",\"assignmentStatus\":\"current\"}";
    }

    private String nested(final String body, final String object, final String field) throws Exception {
        final var tree = this.json.readTree(body);
        final var parent = tree.get(object);
        final var node = parent.get(field);
        return node.asText();
    }

    private void voidIssuance(final SampleElection world) throws Exception {
        final var voter = world.person("Vera", "Voter");
        final var created = this.send("POST", "/issuances", this.issuanceJson(world, voter));
        final var createdBody = created.body();
        final var id = this.text(createdBody, "id");
        final var voidPath = "/issuances/" + id + "/void";
        final var voided = this.send("POST", voidPath, "{\"reason\":\"machine not started\"}");
        final var voidedBody = voided.body();
        final var status = this.text(voidedBody, "status");
        final var stored = world.repository.find(org.sure_vote.entity.BallotIssuance.class, UUID.fromString(id));
        final var issuance = stored.orElseThrow();
        assertEquals(201, created.statusCode());
        assertEquals("accepted", this.text(createdBody, "status"));
        assertEquals(voter.toString(), this.text(createdBody, "personId"));
        assertEquals(200, voided.statusCode());
        assertEquals("voided", status);
        assertEquals(IssuanceStatus.VOIDED, issuance.getStatus());
    }

    private void record(final SampleElection world) throws Exception {
        final var ballotBody = this.ballotJson(world);
        final var imported = this.send("POST", "/ballots", ballotBody);
        final var importedBody = imported.body();
        final var code = world.lastCode;
        final var lookupPath = "/elections/" + world.electionId + "/ballots?code=" + code;
        final var lookedUp = this.send("GET", lookupPath, null);
        final var lookedUpBody = lookedUp.body();
        final var closeBody = this.closeJson(world);
        final var path = "/elections/" + world.electionId + "/close";
        final var closed = this.send("POST", path, closeBody);
        final var closedBody = closed.body();
        final var type = this.text(importedBody, "type");
        final var payload = this.text(importedBody, "payload");
        final var value = this.voteValue(lookedUpBody);
        final var status = this.text(closedBody, "status");
        final var election = world.app.election(world.electionId);
        final var postedPath = "/elections/" + world.electionId + "/lookup";
        final var postedJson = "{\"code\":\"" + code + "\",\"passphrase\":\"correct horse battery\"}";
        final var posted = this.send("POST", postedPath, postedJson);
        final var postedBody = posted.body();
        final var postedValue = this.voteValue(postedBody);
        assertEquals(201, imported.statusCode());
        assertEquals("in-person", type);
        assertFalse(payload.contains(code));
        assertEquals(200, lookedUp.statusCode());
        assertEquals("Y", value);
        assertEquals(200, posted.statusCode());
        assertEquals("Y", postedValue);
        assertFalse(lookedUpBody.contains(code));
        assertEquals(200, closed.statusCode());
        assertEquals("historical", status);
        assertEquals(ElectionStatus.HISTORICAL, election.getStatus());
    }

    private void dispute(final SampleElection world) throws Exception {
        final var ballot = world.castOne(world.slotA, "Y");
        final var code = world.codes.get(ballot.getId());
        final var filed = this.fileDispute(world, code);
        this.assertDisputeHttp(world, code, filed);
    }

    private HttpResponse<String> fileDispute(final SampleElection world, final String code) throws Exception {
        final var path = "/elections/" + world.electionId + "/disputes";
        final var body = "{\"code\":\"" + code + "\",\"statement\":\"the receipt shows a different choice\"}";
        return this.send("POST", path, body);
    }

    private void assertDisputeHttp(
        final SampleElection world,
        final String code,
        final HttpResponse<String> filed
    ) throws Exception {
        final var filedBody = filed.body();
        final var id = this.text(filedBody, "id");
        final var electionPath = "/elections/" + world.electionId + "/disputes";
        final var countPath = electionPath + "/count";
        final var counted = this.sendUnsigned("GET", countPath, null);
        final var countedBody = counted.body();
        final var lookupPath = electionPath + "/lookup";
        final var lookupBody = "{\"code\":\"" + code + "\"}";
        final var looked = this.send("POST", lookupPath, lookupBody);
        final var lookedBody = looked.body();
        final var hidden = this.sendUnsigned("GET", electionPath, null);
        final var hiddenBody = hidden.body();
        final var statusPath = "/disputes/" + id + "/status";
        final var denied = this.sendUnsigned("POST", statusPath, "{\"status\":\"noted\"}");
        final var noted = this.send("POST", statusPath, "{\"status\":\"noted\"}");
        final var notedBody = noted.body();
        assertEquals(201, filed.statusCode());
        assertEquals("open", this.text(filedBody, "status"));
        assertEquals("the receipt shows a different choice", this.text(filedBody, "statement"));
        assertFalse(filedBody.contains(code));
        assertEquals(200, counted.statusCode());
        assertEquals("1", this.text(countedBody, "count"));
        assertFalse(countedBody.contains("receipt"));
        assertEquals(200, looked.statusCode());
        assertTrue(lookedBody.contains("the receipt shows a different choice"));
        assertEquals(400, hidden.statusCode());
        assertEquals("X-Actor-Id is required", this.text(hiddenBody, "error"));
        assertEquals(400, denied.statusCode());
        assertEquals(200, noted.statusCode());
        assertEquals("noted", this.text(notedBody, "status"));
    }

    private HttpResponse<String> sendUnsigned(final String method, final String path, final String body)
        throws Exception {
        final var request = this.unsigned(method, path, body);
        return this.client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest unsigned(final String method, final String path, final String body) {
        final var builder = HttpRequest.newBuilder();
        final var uri = URI.create(this.base + path);
        builder.uri(uri);
        builder.header("Content-Type", "application/json");
        this.addBody(builder, method, body);
        return builder.build();
    }

    private void use(final SampleElection world, final Work work) throws Exception {
        final var server = ApiServer.start(world.app, 0);
        final var http = HttpClient.newHttpClient();
        final var mapper = new ObjectMapper();
        final var actor = world.actor.toString();
        this.client = http;
        this.json = mapper;
        this.actorId = actor;
        this.base = "http://127.0.0.1:" + server.port();
        try {
            work.run();
        }
        finally {
            server.close();
        }
    }

    private HttpResponse<String> send(final String method, final String path, final String body) throws Exception {
        final var request = this.request(method, path, body);
        return this.client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(final String method, final String path, final String body) {
        final var builder = HttpRequest.newBuilder();
        final var uri = URI.create(this.base + path);
        builder.uri(uri);
        builder.header("Content-Type", "application/json");
        builder.header("X-Actor-Id", this.actorId);
        this.addBody(builder, method, body);
        return builder.build();
    }

    private void addBody(final HttpRequest.Builder builder, final String method, final String body) {
        if ("POST".equals(method)) {
            final var publisher = HttpRequest.BodyPublishers.ofString(this.textOrEmpty(body));
            builder.POST(publisher);
        }
        else {
            builder.GET();
        }
    }

    private String textOrEmpty(final String body) {
        if (body == null) {
            return "";
        }
        else {
            return body;
        }
    }

    private String text(final String body, final String field) throws Exception {
        final var tree = this.json.readTree(body);
        final var node = tree.get(field);
        return node.asText();
    }

    private String voteValue(final String body) throws Exception {
        final var tree = this.json.readTree(body);
        final var votes = tree.get("votes");
        final var vote = votes.get(0);
        final var value = vote.get("value");
        return value.asText();
    }

    private String signatureJson(final String signature) {
        return "{\"signature\":\"" + signature + "\"}";
    }

    private String issuanceJson(final SampleElection world, final UUID voter) {
        return "{\"tenantId\":\"" + world.tenantId
            + "\",\"personId\":\"" + voter
            + "\",\"electionId\":\"" + world.electionId
            + "\",\"recordingPlaceId\":\"" + world.placeId
            + "\"}";
    }

    private String ballotJson(final SampleElection world) throws Exception {
        final var sealed = world.sealed(world.slotA, "Y");
        final var votes = sealed.votes();
        final var vote = votes.getFirst();
        final var candidateId = vote.raceCandidateId();
        final var entryTimestamp = vote.entryTimestamp();
        final var tenantId = sealed.tenantId();
        final var electionId = sealed.electionId();
        final var precinctId = sealed.votingPrecinctId();
        final var deviceId = sealed.recordingDeviceId();
        final var placeId = sealed.recordingPlaceId();
        final var recordedBy = sealed.recordedBy();
        final var timestamp = sealed.timestamp();
        final var voteRow = new LinkedHashMap<String, String>();
        final var body = new LinkedHashMap<String, Object>();
        final var rows = List.of(voteRow);
        voteRow.put("raceCandidateId", candidateId.toString());
        voteRow.put("value", vote.value());
        voteRow.put("signature", vote.signature());
        voteRow.put("entryTimestamp", entryTimestamp.toString());
        voteRow.put("nonce", vote.nonce());
        voteRow.put("entrySignature", vote.entrySignature());
        body.put("tenantId", tenantId.toString());
        body.put("electionId", electionId.toString());
        body.put("votingPrecinctId", precinctId.toString());
        body.put("type", "in-person");
        body.put("recordingDeviceId", deviceId.toString());
        body.put("recordingPlaceId", placeId.toString());
        body.put("recordedBy", recordedBy.toString());
        body.put("timestamp", timestamp.toString());
        body.put("voterAccessHash", sealed.voterAccessHash());
        body.put("signature", sealed.signature());
        body.put("votes", rows);
        return this.json.writeValueAsString(body);
    }

    private String closeJson(final SampleElection world) throws Exception {
        final var chains = world.repository.list(org.sure_vote.entity.VoteChain.class);
        final var chain = chains.getFirst();
        final var raw = Digests.unhex(chain.getHash());
        final var seed = world.operator.seed();
        final var signature = Signatures.sign(seed, raw);
        final var payload = world.app.canvassPayload(world.electionId);
        final var canvassHash = Digests.sha256Hex(payload);
        final var canvassRaw = Digests.unhex(canvassHash);
        final var canvassSignature = Signatures.sign(seed, canvassRaw);
        final var row = new LinkedHashMap<String, String>();
        final var body = new LinkedHashMap<String, Object>();
        final var rows = List.of(row);
        row.put("raceId", world.raceId.toString());
        row.put("signature", signature);
        body.put("chains", rows);
        body.put("canvassSignature", canvassSignature);
        return this.json.writeValueAsString(body);
    }

    @FunctionalInterface
    private interface Work {
        void run() throws Exception;
    }
}
