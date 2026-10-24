package org.sure_vote.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sure_vote.repository.InMemorySureVoteRepository;
import org.sure_vote.service.SureVote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ApiServerTest {
    private ApiServer server;
    private HttpClient client;
    private ObjectMapper json;
    private UUID actor;
    private String base;

    @BeforeEach
    void setUp() {
        final var repository = new InMemorySureVoteRepository();
        final var app = new SureVote(repository);
        this.server = ApiServer.start(app);
        this.client = HttpClient.newHttpClient();
        this.json = new ObjectMapper();
        this.actor = UUID.randomUUID();
        this.base = "http://127.0.0.1:" + this.server.port();
    }

    @AfterEach
    void tearDown() {
        this.server.close();
    }

    @Test
    void health() throws Exception {
        final var response = this.send("GET", "/health", null);
        final var code = response.statusCode();
        final var body = response.body();
        final var tree = this.json.readTree(body);
        final var node = tree.get("status");
        final var text = node.asText();
        assertEquals(200, code);
        assertEquals("ok", text);
    }

    @Test
    void showcasePersonRejectsSsnAndOmitsSecrets() throws Exception {
        final var created = this.send("POST", "/tenants", this.tenantJson());
        final var tenantId = this.idOf(created.body());
        final var rejected = this.send("POST", "/persons", this.secretPerson(tenantId));
        final var accepted = this.send("POST", "/persons", this.plainPerson(tenantId));
        final var rejectedBody = rejected.body();
        final var acceptedBody = accepted.body();
        final var message = this.error(rejectedBody);
        final var tree = this.json.readTree(acceptedBody);
        assertEquals(201, created.statusCode());
        assertEquals(400, rejected.statusCode());
        assertEquals("A showcase tenant rejects ssn and voter id", message);
        assertEquals(201, accepted.statusCode());
        assertFalse(tree.has("ssn"));
        assertFalse(tree.has("voterId"));
        assertFalse(tree.has("email"));
        assertFalse(acceptedBody.contains("123-45-6789"));
        assertFalse(acceptedBody.contains("voter-1"));
    }

    @Test
    void missingLookupCodeMatchesAMiss() throws Exception {
        final var response = this.send(
            "GET",
            "/elections/00000000-0000-0000-0000-000000000001/ballots",
            null
        );
        final var message = this.error(response.body());
        assertEquals(400, response.statusCode());
        assertEquals("no ballot for that code", message);
    }

    @Test
    void writesRequireAnActor() throws Exception {
        final var request = this.request("POST", "/tenants", this.tenantJson(), false);
        final var response = this.client.send(request, HttpResponse.BodyHandlers.ofString());
        final var message = this.error(response.body());
        assertEquals(400, response.statusCode());
        assertEquals("X-Actor-Id is required", message);
    }

    @Test
    void unknownPathIsNotFound() throws Exception {
        final var response = this.send("GET", "/missing", null);
        final var message = this.error(response.body());
        assertEquals(404, response.statusCode());
        assertEquals("not found", message);
    }

    @Test
    void invalidJsonIsRejected() throws Exception {
        final var response = this.send("POST", "/tenants", "{");
        final var message = this.error(response.body());
        assertEquals(400, response.statusCode());
        assertEquals("invalid json", message);
    }

    @Test
    void invalidActorIdIsRejected() throws Exception {
        final var response = this.sendAs("not-a-uuid");
        final var message = this.error(response.body());
        assertEquals(400, response.statusCode());
        assertEquals("invalid id", message);
    }

    @Test
    void invalidTenantKindIsRejected() throws Exception {
        final var response = this.send("POST", "/tenants", this.badKind());
        final var message = this.error(response.body());
        assertEquals(400, response.statusCode());
        assertEquals("Unknown tenant kind: nope", message);
    }

    @Test
    void invalidElectionTimestampIsRejected() throws Exception {
        final var response = this.send("POST", "/elections", "{\"votingStart\":\"nope\"}");
        final var message = this.error(response.body());
        assertEquals(400, response.statusCode());
        assertEquals("invalid timestamp", message);
    }

    @Test
    void invalidIdsAreRejected() throws Exception {
        final var election = this.send("GET", "/elections/not-a-uuid", null);
        final var definition = this.send("POST", "/elections/not-a-uuid/definition", "{}");
        final var issuance = this.send("POST", "/issuances/not-a-uuid/void", "{\"reason\":\"x\"}");
        final var electionMessage = this.error(election.body());
        final var definitionMessage = this.error(definition.body());
        final var issuanceMessage = this.error(issuance.body());
        assertEquals(400, election.statusCode());
        assertEquals("invalid id", electionMessage);
        assertEquals(400, definition.statusCode());
        assertEquals("invalid id", definitionMessage);
        assertEquals(400, issuance.statusCode());
        assertEquals("invalid id", issuanceMessage);
    }

    @Test
    void anUnknownElectionIsMissing() throws Exception {
        final var response = this.send("GET", "/elections/00000000-0000-0000-0000-000000000001", null);
        final var message = this.error(response.body());
        assertEquals(400, response.statusCode());
        assertEquals("Missing Election", message);
    }

    @Test
    void lookupQueriesThatDoNotNameABallotMatchAMiss() throws Exception {
        final var path = "/elections/00000000-0000-0000-0000-000000000001/ballots";
        final var other = this.send("GET", path + "?other=1", null);
        final var empty = this.send("GET", path + "?code=", null);
        final var encoded = this.send("GET", path + "?x=1&code=%20ab", null);
        final var otherMessage = this.error(other.body());
        final var emptyMessage = this.error(empty.body());
        final var encodedMessage = this.error(encoded.body());
        assertEquals(400, other.statusCode());
        assertEquals("no ballot for that code", otherMessage);
        assertEquals(400, empty.statusCode());
        assertEquals("no ballot for that code", emptyMessage);
        assertEquals(400, encoded.statusCode());
        assertEquals("no ballot for that code", encodedMessage);
    }

    @Test
    void aNullBodyIsAnInternalError() throws Exception {
        final var response = this.send("POST", "/tenants", "null");
        final var message = this.error(response.body());
        assertEquals(500, response.statusCode());
        assertEquals("internal error", message);
    }

    @Test
    void aTrailingSlashStillFindsHealth() throws Exception {
        final var response = this.send("GET", "/health/", null);
        final var body = response.body();
        final var tree = this.json.readTree(body);
        final var node = tree.get("status");
        final var text = node.asText();
        assertEquals(200, response.statusCode());
        assertEquals("ok", text);
    }

    @Test
    void theWrongMethodIsNotFound() throws Exception {
        final var health = this.send("POST", "/health", "{}");
        final var tenants = this.send("GET", "/tenants", null);
        final var healthMessage = this.error(health.body());
        final var tenantsMessage = this.error(tenants.body());
        assertEquals(404, health.statusCode());
        assertEquals("not found", healthMessage);
        assertEquals(404, tenants.statusCode());
        assertEquals("not found", tenantsMessage);
    }

    private HttpResponse<String> send(final String method, final String path, final String body) throws Exception {
        final var request = this.request(method, path, body, true);
        return this.client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(
        final String method,
        final String path,
        final String body,
        final boolean includeActor
    ) {
        final var builder = HttpRequest.newBuilder();
        final var uri = URI.create(this.base + path);
        builder.uri(uri);
        builder.header("Content-Type", "application/json");
        this.addActor(builder, includeActor);
        this.addBody(builder, method, body);
        return builder.build();
    }

    private void addActor(final HttpRequest.Builder builder, final boolean includeActor) {
        if (includeActor) {
            builder.header("X-Actor-Id", this.actor.toString());
        }
    }

    private void addBody(final HttpRequest.Builder builder, final String method, final String body) {
        if ("POST".equals(method)) {
            final var publisher = HttpRequest.BodyPublishers.ofString(this.text(body));
            builder.POST(publisher);
        }
        else {
            builder.GET();
        }
    }

    private String text(final String body) {
        if (body == null) {
            return "";
        }
        else {
            return body;
        }
    }

    private UUID idOf(final String body) throws Exception {
        final var tree = this.json.readTree(body);
        final var node = tree.get("id");
        final var text = node.asText();
        return UUID.fromString(text);
    }

    private String error(final String body) throws Exception {
        final var tree = this.json.readTree(body);
        final var node = tree.get("error");
        return node.asText();
    }

    private String tenantJson() {
        return "{\"name\":\"Demo\",\"kind\":\"showcase\",\"residencyRegion\":\"us-east\",\"status\":\"active\"}";
    }

    private String secretPerson(final UUID tenantId) {
        return "{\"tenantId\":\"" + tenantId + "\",\"firstName\":\"Ada\",\"lastName\":\"One\",\"ssn\":\"123-45-6789\"}";
    }

    private String plainPerson(final UUID tenantId) {
        return "{\"tenantId\":\""
            + tenantId
            + "\",\"firstName\":\"Bea\",\"lastName\":\"Two\",\"voterId\":null}";
    }

    private String badKind() {
        return "{\"name\":\"Demo\",\"kind\":\"nope\",\"residencyRegion\":\"us-east\",\"status\":\"active\"}";
    }

    private HttpResponse<String> sendAs(final String actor) throws Exception {
        final var request = this.actorRequest(actor);
        return this.client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest actorRequest(final String actor) {
        final var builder = HttpRequest.newBuilder();
        final var uri = URI.create(this.base + "/tenants");
        final var publisher = HttpRequest.BodyPublishers.ofString(this.tenantJson());
        builder.uri(uri);
        builder.header("Content-Type", "application/json");
        builder.header("X-Actor-Id", actor);
        builder.POST(publisher);
        return builder.build();
    }
}
