package org.sure_vote.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.sure_vote.api.ApiServer;
import org.sure_vote.entity.BallotType;
import org.sure_vote.entity.ElectionStatus;
import org.sure_vote.entity.IssuanceStatus;
import org.sure_vote.entity.VoteChain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpElectionTest {
    private static final String NONCE = "nonce-0123456789abcdef";

    private HttpClient client;
    private ObjectMapper json;
    private String base;
    private String cookie;

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
    void aSignedMailImportLeavesTheIssuanceIssued() throws Exception {
        final var world = SampleElection.hostedMail();
        this.use(world, () -> this.mailImport(world));
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

    @Test
    void anOperatorSignedRestoreIncidentIsPublic() throws Exception {
        final var world = SampleElection.fptp();
        RestoreFixture.restoredAfterLoss(world);
        this.use(world, () -> this.restoreIncident(world));
    }

    @Test
    void aClosedElectionServesItsSignedBundle() throws Exception {
        final var world = SampleElection.fptp();
        world.castOne(world.slotA, "Y");
        world.close();
        this.use(world, () -> this.bundle(world));
    }

    private void bundle(final SampleElection world) throws Exception {
        final var path = "/elections/" + world.electionId + "/close-bundle";
        final var missing = this.send("GET", path, null);
        final var hashed = this.saved(path + "/hash", null);
        final var hash = this.text(hashed, "hash");
        final var raw = Digests.unhex(hash);
        final var seed = world.operator.seed();
        final var signature = Signatures.sign(seed, raw);
        final var filed = this.saved(path, "{\"signature\":\"" + signature + "\"}");
        final var downloaded = this.send("GET", path, null);
        final var headers = downloaded.headers();
        final var heads = this.send("GET", "/elections/" + world.electionId + "/heads", null);
        final var headsBody = heads.body();
        final var sentHash = headers.firstValue("SureVote-Bundle-Hash");
        final var sentSignature = headers.firstValue("SureVote-Bundle-Signature");
        assertEquals(400, missing.statusCode());
        assertTrue(filed.contains(hash));
        assertEquals(200, downloaded.statusCode());
        assertEquals(hash, Digests.sha256Hex(downloaded.body()));
        assertEquals(hash, sentHash.orElseThrow());
        assertEquals(signature, sentSignature.orElseThrow());
        assertTrue(headsBody.contains("\"bundleHash\":\"" + hash + "\""));
    }

    private void restoreIncident(final SampleElection world) throws Exception {
        final var path = "/elections/" + world.electionId + "/restore-incidents";
        final var draft = "{\"reason\":\"Disk lost\",\"timestamp\":\"2026-11-06T09:30:00.250Z\"}";
        final var hashed = this.saved(path + "/hash", draft);
        final var hash = this.text(hashed, "hash");
        final var raw = Digests.unhex(hash);
        final var seed = world.operator.seed();
        final var signature = Signatures.sign(seed, raw);
        final var filed = this.saved(path, this.restoreJson(signature));
        final var listed = this.send("GET", path, null);
        final var listBody = listed.body();
        final var unsigned = this.send("POST", path, this.restoreJson("00".repeat(64)));
        assertTrue(filed.contains(hash));
        assertEquals(200, listed.statusCode());
        assertTrue(listBody.contains("\"signature\":\"" + signature + "\""));
        assertTrue(listBody.contains("\\\"reason\\\":\\\"Disk lost\\\""));
        assertEquals(400, unsigned.statusCode());
    }

    private String restoreJson(final String signature) {
        return "{\"reason\":\"Disk lost\",\"timestamp\":\"2026-11-06T09:30:00.250Z\""
            + ",\"signature\":\"" + signature + "\"}";
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
        final var body = "{\"name\":\"Nope\",\"votingStart\":\"2026-11-03T00:00:00Z\""
            + ",\"votingEnd\":\"2026-11-04T00:00:00Z\"}";
        final var created = this.send("POST", "/showcase/elections", body);
        final var createdBody = created.body();
        final var message = this.text(createdBody, "error");
        assertEquals(400, created.statusCode());
        assertEquals("only a showcase tenant keeps host keys", message);
    }

    private void listLocality(final SampleElection world) throws Exception {
        final var body = "{\"name\":\"Town\",\"type\":\"city\"}";
        final var created = this.send("POST", "/localities", body);
        final var path = "/workspace";
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
        final var report = this.visitorReport(world, election);
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
        assertTrue(report.contains("\"recordingPlaceId\":\"" + place + "\""));
        assertTrue(report.contains("\"received\":1"));
        assertTrue(report.contains("\"matched\":true"));
    }

    private String visitorReport(final SampleElection world, final String election) throws Exception {
        final var path = "/elections/" + election + "/reconciliation";
        final var cookie = this.visitorCookie(world);
        final var request = this.withCookie(path, cookie);
        final var response = this.client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return response.body();
    }

    private HttpRequest withCookie(final String path, final String cookie) {
        final var builder = HttpRequest.newBuilder();
        final var uri = URI.create(this.base + path);
        builder.uri(uri);
        builder.header("Cookie", cookie);
        return builder.build();
    }

    private String visitorCookie(final SampleElection world) throws Exception {
        final var started = this.visitorSession(world);
        final var headers = started.headers();
        final var setCookie = headers.firstValue("Set-Cookie");
        final var cookie = setCookie.orElseThrow();
        return cookie.substring(0, cookie.indexOf(';'));
    }

    private HttpResponse<String> visitorSession(final SampleElection world) throws Exception {
        world.app.provisionDemoTenant(world.tenantId, "Demo", "us-east");
        return this.sendUnsigned("POST", "/showcase/session", null);
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
        final var keyId = "validator:" + world.validatorId;
        final var seed = world.validatorKey.seed();
        final var validation = this.validationJson(world, chain);
        final var forged = this.signed(path + "/validations", validation, keyId, new byte[32], NONCE);
        final var response = this.signed(path + "/validations", validation, keyId, seed, NONCE);
        final var filed = response.body();
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
        assertEquals(401, forged.statusCode());
        assertEquals("request signature is invalid", this.text(forged.body(), "error"));
        assertEquals(201, response.statusCode());
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
        final var path = "/workspace";
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
        return "{\"name\":\"Town\",\"type\":\"city\"}";
    }

    private String addressJson(final SampleElection world, final String locality) {
        return "{\"localityId\":\"" + locality
            + "\",\"street1\":\"1 Main\",\"postalCode\":\"00000\"}";
    }

    private String precinctJson(final SampleElection world, final String address) {
        return "{\"name\":\"Precinct 1\",\"addressId\":\"" + address + "\"}";
    }

    private String districtJson(final SampleElection world, final String locality) {
        return "{\"localityId\":\"" + locality
            + "\",\"name\":\"Ward 1\",\"type\":\"city-council\"}";
    }

    private String linkJson(final SampleElection world, final String precinct, final String district) {
        return "{\"votingPrecinctId\":\"" + precinct
            + "\",\"votingDistrictId\":\"" + district
            + "\",\"districtType\":\"city-council\"}";
    }

    private String seatJson(final SampleElection world, final String locality) {
        return "{\"localityId\":\"" + locality
            + "\",\"name\":\"Mayor\",\"jurisdiction\":\"local\",\"type\":\"mayor\""
            + ",\"appointments\":1,\"termLength\":4}";
    }

    private String termJson(final SampleElection world, final String seat) {
        return "{\"seatId\":\"" + seat + "\"}";
    }

    private String showcaseElectionJson(final SampleElection world, final String locality) {
        final var now = Instant.now();
        final var start = now.minus(Duration.ofHours(1));
        final var end = now.plus(Duration.ofDays(1));
        return "{\"localityId\":\"" + locality
            + "\",\"name\":\"General\",\"votingStart\":\"" + start
            + "\",\"votingEnd\":\"" + end + "\"}";
    }

    private String raceJson(final SampleElection world, final String election, final String term) {
        return "{\"electionId\":\"" + election
            + "\",\"termId\":\"" + term
            + "\",\"name\":\"Mayor\",\"type\":\"general\",\"method\":\"first-past-the-post\"}";
    }

    private String personJson(final SampleElection world, final String first, final String last) {
        return "{\"firstName\":\"" + first
            + "\",\"lastName\":\"" + last + "\"}";
    }

    private String candidateJson(final SampleElection world, final String person) {
        return "{\"personId\":\"" + person + "\"}";
    }

    private String slotJson(final SampleElection world, final String race, final String candidate) {
        return "{\"raceId\":\"" + race
            + "\",\"candidateId\":\"" + candidate
            + "\",\"writeIn\":false}";
    }

    private String styleJson(
        final SampleElection world,
        final String election,
        final String precinct,
        final String race
    ) {
        return "{\"electionId\":\"" + election
            + "\",\"votingPrecinctId\":\"" + precinct
            + "\",\"raceId\":\"" + race + "\"}";
    }

    private String placeJson(final SampleElection world, final String locality, final String address) {
        return "{\"localityId\":\"" + locality
            + "\",\"addressId\":\"" + address
            + "\",\"name\":\"Town Hall\",\"kind\":\"polling-place\"}";
    }

    private String showcaseDeviceJson(final SampleElection world) {
        return "{\"name\":\"Machine 1\",\"kind\":\"polling-place-machine\"}";
    }

    private String deviceJson(final SampleElection world) {
        final var key = Signatures.generate();
        final var publicKey = key.publicKey();
        return "{\"name\":\"Machine 2\",\"kind\":\"polling-place-machine\""
            + ",\"publicKey\":\"" + publicKey
            + "\",\"status\":\"active\"}";
    }

    private String assignmentJson(final SampleElection world, final String election, final String precinct) {
        return "{\"electionId\":\"" + election
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
        assertEquals("issued", this.text(createdBody, "status"));
        assertEquals(voter.toString(), this.text(createdBody, "personId"));
        assertEquals(200, voided.statusCode());
        assertEquals("voided", status);
        assertEquals(IssuanceStatus.VOIDED, issuance.getStatus());
    }

    private void mailImport(final SampleElection world) throws Exception {
        final var personId = world.person("Mae", "Voter");
        final var mailed = this.mailedJson(world, personId);
        final var sealed = world.sealedMail(BallotType.MAIL_IN, personId, world.actor);
        final var keyId = "import:" + world.electionId;
        final var body = this.ballotJson(sealed, "mail-in");
        final var imported = this.signed("/ballots", body, keyId, world.importKey.seed(), NONCE);
        final var path = "/elections/" + world.electionId + "/reconciliation";
        final var report = this.send("GET", path, null);
        final var visitor = this.sendUnsigned("GET", path, null);
        final var rows = this.json.readTree(report.body());
        final var row = rows.get(0);
        final var issuances = world.repository.list(org.sure_vote.entity.BallotIssuance.class);
        final var issuance = issuances.getFirst();
        final var place = row.get("recordingPlaceId");
        final var matched = row.get("matched");
        final var mail = row.get("mail");
        assertEquals(201, mailed.statusCode());
        assertEquals(201, imported.statusCode());
        assertEquals(IssuanceStatus.ISSUED, issuance.getStatus());
        assertEquals(200, report.statusCode());
        assertEquals(world.officeId.toString(), place.asText());
        assertEquals(1, this.count(row, "issued"));
        assertEquals(0, this.count(row, "received"));
        assertEquals(1, this.count(row, "ballots"));
        assertFalse(matched.asBoolean());
        assertTrue(mail.asBoolean());
        assertEquals(401, visitor.statusCode());
    }

    private HttpResponse<String> mailedJson(final SampleElection world, final UUID personId) throws Exception {
        final var body = "{\"personId\":\"" + personId
            + "\",\"electionId\":\"" + world.electionId
            + "\",\"recordingPlaceId\":\"" + world.officeId
            + "\"}";
        return this.send("POST", "/mail-issuances", body);
    }

    private int count(final com.fasterxml.jackson.databind.JsonNode row, final String field) {
        final var node = row.get(field);
        return node.asInt();
    }

    private void record(final SampleElection world) throws Exception {
        final var ballotBody = this.ballotJson(world);
        final var keyId = "import:" + world.electionId;
        final var imported = this.signed("/ballots", ballotBody, keyId, world.importKey.seed(), NONCE);
        final var replayed = this.signed("/ballots", ballotBody, keyId, world.importKey.seed(), NONCE);
        final var unsigned = this.send("POST", "/ballots", ballotBody);
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
        final var byCode = this.send("POST", "/lookup", postedJson);
        final var byCodeValue = this.voteValue(byCode.body());
        assertEquals(201, imported.statusCode());
        assertEquals(401, replayed.statusCode());
        assertEquals("request was already used", this.text(replayed.body(), "error"));
        assertEquals(401, unsigned.statusCode());
        assertEquals("a signed request is required", this.text(unsigned.body(), "error"));
        assertEquals("in-person", type);
        assertFalse(payload.contains(code));
        assertEquals(200, lookedUp.statusCode());
        assertEquals("Y", value);
        assertEquals(200, posted.statusCode());
        assertEquals("Y", postedValue);
        assertEquals(200, byCode.statusCode());
        assertEquals("Y", byCodeValue);
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
        assertEquals(401, hidden.statusCode());
        assertEquals("sign-in required", this.text(hiddenBody, "error"));
        assertEquals(401, denied.statusCode());
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

    private HttpResponse<String> signed(
        final String path,
        final String body,
        final String keyId,
        final byte[] seed,
        final String nonce
    ) throws Exception {
        final var bytes = body.getBytes(StandardCharsets.UTF_8);
        final var now = Instant.now();
        final var timestamp = now.toString();
        final var unsigned = new SignedRequest("POST", path, keyId, timestamp, nonce, bytes, null);
        final var signature = SignedRequests.sign(seed, unsigned);
        final var builder = HttpRequest.newBuilder();
        builder.uri(URI.create(this.base + path));
        builder.header("Content-Type", "application/json");
        builder.header("SureVote-Key-Id", keyId);
        builder.header("SureVote-Timestamp", timestamp);
        builder.header("SureVote-Nonce", nonce);
        builder.header("SureVote-Signature", signature);
        builder.POST(HttpRequest.BodyPublishers.ofByteArray(bytes));
        return this.client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void use(final SampleElection world, final Work work) throws Exception {
        final var server = ApiServer.start(world.app, 0);
        final var http = HttpClient.newHttpClient();
        final var mapper = new ObjectMapper();
        final var cookie = "surevote_session=" + world.officialSession();
        this.client = http;
        this.json = mapper;
        this.cookie = cookie;
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
        builder.header("Cookie", this.cookie);
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
        return "{\"personId\":\"" + voter
            + "\",\"electionId\":\"" + world.electionId
            + "\",\"recordingPlaceId\":\"" + world.placeId
            + "\"}";
    }

    private String ballotJson(final SampleElection world) throws Exception {
        return this.ballotJson(world.sealed(world.slotA, "Y"), "in-person");
    }

    private String ballotJson(final ImportBallot sealed, final String wireType) throws Exception {
        final var votes = sealed.votes();
        final var vote = votes.getFirst();
        final var candidateId = vote.raceCandidateId();
        final var entryTimestamp = vote.entryTimestamp();
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
        body.put("electionId", electionId.toString());
        body.put("votingPrecinctId", precinctId.toString());
        body.put("type", wireType);
        body.put("recordingDeviceId", deviceId.toString());
        body.put("recordingPlaceId", placeId.toString());
        body.put("recordedBy", recordedBy.toString());
        body.put("timestamp", timestamp.toString());
        body.put("voterAccessHash", sealed.voterAccessHash());
        body.put("signature", sealed.signature());
        body.put("votes", rows);
        this.putPerson(body, sealed.personId());
        return this.json.writeValueAsString(body);
    }

    private void putPerson(final LinkedHashMap<String, Object> body, final UUID personId) {
        if (personId != null) {
            body.put("personId", personId.toString());
        }
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
