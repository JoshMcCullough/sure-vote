package org.sure_vote.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sure_vote.api.ApiServer;
import org.sure_vote.entity.Person;
import org.sure_vote.entity.Session;
import org.sure_vote.repository.JdbcSureVoteRepository;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresWriterHttpTest {
    private static final String ROLE = "surevote_app";

    private static final String SECRET = "writer";

    private static PostgreSQLContainer<?> postgres;

    private static JdbcSureVoteRepository owner;

    private static JdbcSureVoteRepository writer;

    private final HttpClient client = HttpClient.newHttpClient();

    private final ObjectMapper json = new ObjectMapper();

    private String base;

    @BeforeAll
    static void start() throws SQLException {
        final var container = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres = container;
        container.start();
        PostgresWriterHttpTest.open(container);
    }

    private static void open(final PostgreSQLContainer<?> container) throws SQLException {
        final var url = container.getJdbcUrl();
        final var user = container.getUsername();
        final var password = container.getPassword();
        JdbcSureVoteRepository.migrate(url, user, password);
        owner = JdbcSureVoteRepository.open(url, user, password);
        PostgresWriterHttpTest.role(url, user, password);
        JdbcSureVoteRepository.grantWriter(url, user, password, ROLE);
        writer = JdbcSureVoteRepository.open(url, ROLE, SECRET);
    }

    private static void role(final String url, final String user, final String password) throws SQLException {
        final var connection = DriverManager.getConnection(url, user, password);
        final var statement = connection.createStatement();
        try {
            statement.execute("create role " + ROLE + " login password '" + SECRET + "'");
        }
        finally {
            statement.close();
            connection.close();
        }
    }

    @AfterAll
    static void stop() {
        final var container = postgres;
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void anOfficialWorksInTheirTenantThroughTheWriterRole() throws Exception {
        final var world = SampleElection.fptpOn(owner);
        final var other = SampleElection.fptpOn(owner);
        final var server = ApiServer.start(this.writerApp(world), 0);
        this.base = "http://127.0.0.1:" + server.port();
        try {
            this.officialFlow(world, other);
        }
        finally {
            server.close();
        }
    }

    private SureVote writerApp(final SampleElection world) {
        final var app = new SureVote(writer);
        world.admin();
        app.enableDevSignIn();
        return app;
    }

    @Test
    void theWriterRoleCannotDeleteOrReadOutsideATenant() {
        final var world = SampleElection.fptpOn(owner);
        final var unscoped = writer.list(Person.class);
        final var scoped = writer.inTenant(world.tenantId, () -> writer.list(Person.class));
        assertEquals(List.of(), unscoped);
        assertFalse(scoped.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> JdbcSureVoteRepository.grantWriter("", "", "", "Bad;"));
        assertThrows(RuntimeException.class, () -> PostgresWriterHttpTest.delete());
    }

    private static void delete() {
        try {
            final var connection = DriverManager.getConnection(postgres.getJdbcUrl(), ROLE, SECRET);
            final var statement = connection.createStatement();
            try {
                statement.execute("delete from session");
            }
            finally {
                statement.close();
                connection.close();
            }
        }
        catch (final SQLException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private void officialFlow(final SampleElection world, final SampleElection other) throws Exception {
        final var admin = this.session(this.post("/auth/dev/sign-in", this.admin(world), null));
        final var created = this.post(
            "/system/tenants/" + world.tenantId + "/officials",
            "{\"name\":\"Clerk\"}",
            admin
        );
        final var officialId = this.field(created.body(), "/official/id");
        final var official = this.session(this.post("/auth/dev/sign-in", this.official(world, officialId), null));
        final var person = this.post("/persons", "{\"firstName\":\"Ada\",\"lastName\":\"Writer\"}", official);
        final var workspace = this.get("/workspace", official);
        final var foreign = this.get("/elections/" + other.electionId + "/disputes", official);
        final var ballot = this.ballot(world);
        final var imported = this.signed("/ballots", ballot, world);
        final var lookup = this.get("/elections/" + world.electionId + "/ballots?code=" + world.lastCode, null);
        final var sessions = writer.list(Session.class);
        final var listed = workspace.body();
        assertEquals(201, created.statusCode());
        assertEquals(201, person.statusCode());
        assertEquals(200, workspace.statusCode());
        assertTrue(listed.contains(String.valueOf(world.localityId)));
        assertFalse(listed.contains(String.valueOf(other.localityId)));
        assertEquals(400, foreign.statusCode());
        assertEquals("Missing Election", this.field(foreign.body(), "/error"));
        assertEquals(201, imported.statusCode());
        assertEquals(200, lookup.statusCode());
        assertEquals(2, sessions.size());
    }

    private String admin(final SampleElection world) {
        return "{\"kind\":\"system-admin\",\"id\":\"" + world.systemAdmin + "\"}";
    }

    private String official(final SampleElection world, final String id) {
        return "{\"kind\":\"official\",\"id\":\"" + id + "\",\"tenantId\":\"" + world.tenantId + "\"}";
    }

    private String ballot(final SampleElection world) throws Exception {
        final var sealed = world.sealed(world.slotA, "Y");
        final var votes = sealed.votes();
        final var vote = votes.getFirst();
        final var row = new LinkedHashMap<String, String>();
        final var body = new LinkedHashMap<String, Object>();
        row.put("raceCandidateId", String.valueOf(vote.raceCandidateId()));
        row.put("value", vote.value());
        row.put("signature", vote.signature());
        row.put("entryTimestamp", String.valueOf(vote.entryTimestamp()));
        row.put("nonce", vote.nonce());
        row.put("entrySignature", vote.entrySignature());
        body.put("electionId", String.valueOf(sealed.electionId()));
        body.put("votingPrecinctId", String.valueOf(sealed.votingPrecinctId()));
        body.put("type", "in-person");
        body.put("recordingDeviceId", String.valueOf(sealed.recordingDeviceId()));
        body.put("recordingPlaceId", String.valueOf(sealed.recordingPlaceId()));
        body.put("recordedBy", String.valueOf(sealed.recordedBy()));
        body.put("timestamp", String.valueOf(sealed.timestamp()));
        body.put("voterAccessHash", sealed.voterAccessHash());
        body.put("signature", sealed.signature());
        body.put("votes", List.of(row));
        return this.json.writeValueAsString(body);
    }

    private HttpResponse<String> signed(final String path, final String body, final SampleElection world)
        throws Exception {
        final var bytes = body.getBytes(StandardCharsets.UTF_8);
        final var keyId = "import:" + world.electionId;
        final var timestamp = String.valueOf(Instant.now());
        final var nonce = "writer-nonce-0123456789";
        final var unsigned = new SignedRequest("POST", path, keyId, timestamp, nonce, bytes, null);
        final var builder = HttpRequest.newBuilder(URI.create(this.base + path));
        builder.header("Content-Type", "application/json");
        builder.header("SureVote-Key-Id", keyId);
        builder.header("SureVote-Timestamp", timestamp);
        builder.header("SureVote-Nonce", nonce);
        builder.header("SureVote-Signature", SignedRequests.sign(world.importKey.seed(), unsigned));
        builder.POST(HttpRequest.BodyPublishers.ofByteArray(bytes));
        return this.client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(final String path, final String body, final String cookie) throws Exception {
        final var builder = this.builder(path, cookie);
        builder.POST(HttpRequest.BodyPublishers.ofString(body));
        return this.client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(final String path, final String cookie) throws Exception {
        final var builder = this.builder(path, cookie);
        builder.GET();
        return this.client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder builder(final String path, final String cookie) {
        final var builder = HttpRequest.newBuilder(URI.create(this.base + path));
        builder.header("Content-Type", "application/json");
        if (cookie != null) {
            builder.header("Cookie", cookie);
            return builder;
        }
        else {
            return builder;
        }
    }

    private String session(final HttpResponse<String> response) {
        final var headers = response.headers();
        final var header = headers.firstValue("Set-Cookie");
        final var value = header.orElseThrow();
        return value.substring(0, value.indexOf(';'));
    }

    private String field(final String body, final String pointer) throws Exception {
        final var tree = this.json.readTree(body);
        final var found = tree.at(pointer);
        return found.asText();
    }
}
