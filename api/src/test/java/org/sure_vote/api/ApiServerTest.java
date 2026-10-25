package org.sure_vote.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sure_vote.entity.PrincipalKind;
import org.sure_vote.entity.SystemAdmin;
import org.sure_vote.repository.InMemorySureVoteRepository;
import org.sure_vote.service.Principal;
import org.sure_vote.service.SureVote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiServerTest {
    private ApiServer server;
    private HttpClient client;
    private ObjectMapper json;
    private String cookie;
    private String official;
    private UUID demo;
    private String base;

    @BeforeEach
    void setUp() {
        final var repository = new InMemorySureVoteRepository();
        final var app = new SureVote(repository);
        this.demo = UUID.randomUUID();
        app.provisionDemoTenant(this.demo, "Demo", "us-east");
        this.official = "surevote_session=" + official(app, this.demo);
        this.server = ApiServer.start(app);
        this.client = HttpClient.newHttpClient();
        this.json = new ObjectMapper();
        this.base = "http://127.0.0.1:" + this.server.port();
        this.cookie = this.visitor();
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
        final var created = this.send("GET", "/showcase/tenant", null);
        final var tenantId = this.idOf(created.body());
        final var rejected = this.send("POST", "/persons", this.secretPerson());
        final var accepted = this.send("POST", "/persons", this.plainPerson());
        final var rejectedBody = rejected.body();
        final var acceptedBody = accepted.body();
        final var message = this.error(rejectedBody);
        final var tree = this.json.readTree(acceptedBody);
        assertEquals(200, created.statusCode());
        assertEquals(this.demo, tenantId);
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
    void writesRequireASession() throws Exception {
        final var request = this.request("POST", "/persons", this.plainPerson(), false);
        final var response = this.client.send(request, HttpResponse.BodyHandlers.ofString());
        final var message = this.error(response.body());
        assertEquals(401, response.statusCode());
        assertEquals("sign-in required", message);
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
        final var response = this.send("POST", "/persons", "{");
        final var message = this.error(response.body());
        assertEquals(400, response.statusCode());
        assertEquals("invalid json", message);
    }

    @Test
    void anUnknownSessionIsRefused() throws Exception {
        final var response = this.sendAs("surevote_session=not-a-session");
        final var message = this.error(response.body());
        assertEquals(401, response.statusCode());
        assertEquals("sign-in required", message);
    }

    @Test
    void tenantsAreNotCreatedOnThePublicPath() throws Exception {
        final var response = this.send("POST", "/tenants", this.tenantJson());
        final var bare = ApiServer.start(new SureVote(new InMemorySureVoteRepository()));
        final var uri = URI.create("http://127.0.0.1:" + bare.port() + "/showcase/tenant");
        final var builder = HttpRequest.newBuilder(uri);
        final var missing = this.client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        final var message = this.error(missing.body());
        bare.close();
        assertEquals(404, response.statusCode());
        assertEquals(404, missing.statusCode());
        assertEquals("no demo tenant", message);
    }

    @Test
    void invalidElectionTimestampIsRejected() throws Exception {
        final var response = this.sendAsOfficial("POST", "/elections", "{\"votingStart\":\"nope\"}");
        final var message = this.error(response.body());
        assertEquals(400, response.statusCode());
        assertEquals("invalid timestamp", message);
    }

    @Test
    void invalidIdsAreRejected() throws Exception {
        final var election = this.send("GET", "/elections/not-a-uuid", null);
        final var definition = this.send("POST", "/elections/not-a-uuid/definition", "{}");
        final var issuance = this.sendAsOfficial("POST", "/issuances/not-a-uuid/void", "{\"reason\":\"x\"}");
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
        final var response = this.send("POST", "/persons", "null");
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
    void aShowcaseElectionWithoutAWindowIsRejected() throws Exception {
        final var response = this.send("POST", "/showcase/elections", "{\"name\":\"Nope\"}");
        final var message = this.error(response.body());
        assertEquals(400, response.statusCode());
        assertEquals("invalid timestamp", message);
    }

    @Test
    void staticFilesStayUnderTheAppRoot() throws Exception {
        final var root = Files.createTempDirectory("sure-vote-app");
        final var page = this.pageFile(root);
        final var script = this.scriptFile(root);
        final var secret = this.secretFile(root);
        final var extra = this.staticServer(root);
        try {
            this.assertStatic(extra, secret);
        }
        finally {
            extra.close();
            this.deleteStatic(script, page, secret, root);
        }
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

    private Path pageFile(final Path root) throws IOException {
        final var page = root.resolve("index.html");
        Files.writeString(page, "<p>SureVote page</p>");
        return page;
    }

    private Path scriptFile(final Path root) throws IOException {
        final var assets = root.resolve("assets");
        final var script = assets.resolve("app.js");
        Files.createDirectories(assets);
        Files.writeString(script, "console.log(1)");
        return script;
    }

    private Path secretFile(final Path root) throws IOException {
        final var parent = root.getParent();
        final var fileName = root.getFileName();
        final var name = fileName.toString();
        final var secret = parent.resolve(name + "-secret.txt");
        Files.writeString(secret, "hidden-secret");
        return secret;
    }

    private ApiServer staticServer(final Path root) {
        final var repository = new InMemorySureVoteRepository();
        final var app = new SureVote(repository);
        return ApiServer.start(app, 0, root);
    }

    private void assertStatic(final ApiServer extra, final Path secret) throws Exception {
        final var origin = "http://127.0.0.1:" + extra.port();
        final var fileName = secret.getFileName();
        final var name = fileName.toString();
        final var page = this.get(origin, "/app");
        final var slash = this.get(origin, "/app/");
        final var workspace = this.get(origin, "/app/workspace");
        final var script = this.get(origin, "/app/assets/app.js");
        final var missing = this.get(origin, "/app/assets/missing.js");
        final var escaped = this.get(origin, "/app/%2e%2e/" + name);
        final var posted = this.getPost(origin);
        final var health = this.get(origin, "/health");
        final var pageBody = page.body();
        final var slashBody = slash.body();
        final var workspaceBody = workspace.body();
        final var headers = page.headers();
        final var header = headers.firstValue("content-type");
        final var type = header.orElse("");
        final var escapedBody = escaped.body();
        assertEquals(200, page.statusCode());
        assertEquals("<p>SureVote page</p>", pageBody);
        assertTrue(type.startsWith("text/html"));
        assertEquals(200, slash.statusCode());
        assertEquals(pageBody, slashBody);
        assertEquals(200, workspace.statusCode());
        assertEquals(pageBody, workspaceBody);
        assertEquals(200, script.statusCode());
        assertEquals("console.log(1)", script.body());
        assertEquals(404, missing.statusCode());
        assertEquals(404, escaped.statusCode());
        assertFalse(escapedBody.contains("hidden-secret"));
        assertEquals(405, posted.statusCode());
        assertEquals(200, health.statusCode());
    }

    private HttpResponse<String> get(final String origin, final String path) throws Exception {
        final var request = this.getRequest(origin, path);
        return this.client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest getRequest(final String origin, final String path) {
        final var builder = HttpRequest.newBuilder();
        final var uri = URI.create(origin + path);
        builder.uri(uri);
        builder.GET();
        return builder.build();
    }

    private HttpResponse<String> getPost(final String origin) throws Exception {
        final var request = this.postApp(origin);
        return this.client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest postApp(final String origin) {
        final var builder = HttpRequest.newBuilder();
        final var uri = URI.create(origin + "/app");
        final var publisher = HttpRequest.BodyPublishers.ofString("");
        builder.uri(uri);
        builder.POST(publisher);
        return builder.build();
    }

    private void deleteStatic(
        final Path script,
        final Path page,
        final Path secret,
        final Path root
    ) throws IOException {
        final var assets = script.getParent();
        Files.deleteIfExists(script);
        Files.deleteIfExists(assets);
        Files.deleteIfExists(page);
        Files.deleteIfExists(secret);
        Files.deleteIfExists(root);
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
            builder.header("Cookie", this.cookie);
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

    private String secretPerson() {
        return "{\"firstName\":\"Ada\",\"lastName\":\"One\",\"ssn\":\"123-45-6789\"}";
    }

    private String plainPerson() {
        return "{\"firstName\":\"Bea\",\"lastName\":\"Two\",\"voterId\":null}";
    }

    private String visitor() {
        try {
            final var response = this.client.send(
                this.request("POST", "/showcase/session", null, false),
                HttpResponse.BodyHandlers.ofString()
            );
            final var headers = response.headers();
            final var header = headers.firstValue("Set-Cookie");
            final var value = header.orElseThrow();
            return value.substring(0, value.indexOf(';'));
        }
        catch (final IOException | InterruptedException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String official(final SureVote app, final UUID tenantId) {
        final var principal = host(app);
        final var created = app.createOfficial(tenantId, "Clerk", principal);
        final var row = created.official();
        return app.devSignIn(PrincipalKind.OFFICIAL, row.getId(), tenantId);
    }

    private static Principal host(final SureVote app) {
        final var admin = new SystemAdmin();
        admin.setId(UUID.randomUUID());
        admin.setName("Host");
        app.bootstrapSystemAdmin(admin);
        app.enableDevSignIn();
        return app.session(app.devSignIn(PrincipalKind.SYSTEM_ADMIN, admin.getId(), null));
    }

    private HttpResponse<String> sendAsOfficial(final String method, final String path, final String body)
        throws Exception {
        final var builder = HttpRequest.newBuilder();
        builder.uri(URI.create(this.base + path));
        builder.header("Content-Type", "application/json");
        builder.header("Cookie", this.official);
        this.addBody(builder, method, body);
        return this.client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendAs(final String actor) throws Exception {
        final var request = this.actorRequest(actor);
        return this.client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest actorRequest(final String actor) {
        final var builder = HttpRequest.newBuilder();
        final var uri = URI.create(this.base + "/persons");
        final var publisher = HttpRequest.BodyPublishers.ofString(this.plainPerson());
        builder.uri(uri);
        builder.header("Content-Type", "application/json");
        builder.header("Cookie", actor);
        builder.POST(publisher);
        return builder.build();
    }
}
