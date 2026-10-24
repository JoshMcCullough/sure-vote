package org.sure_vote.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.sure_vote.api.ApiServer;
import org.sure_vote.entity.ElectionStatus;
import org.sure_vote.entity.IssuanceStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
        assertEquals(201, imported.statusCode());
        assertEquals("in-person", type);
        assertFalse(payload.contains(code));
        assertEquals(200, lookedUp.statusCode());
        assertEquals("Y", value);
        assertFalse(lookedUpBody.contains(code));
        assertEquals(200, closed.statusCode());
        assertEquals("historical", status);
        assertEquals(ElectionStatus.HISTORICAL, election.getStatus());
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
        final var row = new LinkedHashMap<String, String>();
        final var body = new LinkedHashMap<String, Object>();
        final var rows = List.of(row);
        row.put("raceId", world.raceId.toString());
        row.put("signature", signature);
        body.put("chains", rows);
        return this.json.writeValueAsString(body);
    }

    @FunctionalInterface
    private interface Work {
        void run() throws Exception;
    }
}
