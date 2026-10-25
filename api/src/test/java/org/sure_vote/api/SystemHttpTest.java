package org.sure_vote.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sure_vote.entity.SystemAdmin;
import org.sure_vote.repository.InMemorySureVoteRepository;
import org.sure_vote.service.SureVote;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemHttpTest {
    private static final String COUNTY =
        "{\"name\":\"County\",\"kind\":\"production\",\"residencyRegion\":\"us-east\"}";

    private static final String BAD_KIND = "{\"name\":\"County\",\"kind\":\"nope\"}";

    private ApiServer server;
    private HttpClient client;
    private ObjectMapper json;
    private UUID admin;
    private String base;

    @BeforeEach
    void setUp() {
        final var app = new SureVote(new InMemorySureVoteRepository());
        final var first = new SystemAdmin();
        this.admin = UUID.randomUUID();
        first.setId(this.admin);
        first.setName("Host");
        app.bootstrapSystemAdmin(first);
        this.server = ApiServer.start(app);
        this.client = HttpClient.newHttpClient();
        this.json = new ObjectMapper();
        this.base = "http://127.0.0.1:" + this.server.port();
    }

    @AfterEach
    void tearDown() {
        this.server.close();
    }

    @Test
    void anAdminManagesTenants() throws Exception {
        final var created = this.send("POST", "/system/tenants", COUNTY, this.admin);
        final var id = this.field(created, "id");
        final var suspended = this.send("PATCH", "/system/tenants/" + id, "{\"status\":\"suspended\"}", this.admin);
        final var renamed = this.send("PATCH", "/system/tenants/" + id, "{\"name\":\"Shire\"}", this.admin);
        final var listed = this.send("GET", "/system/tenants", null, this.admin);
        final var rows = this.list(listed, "tenants");
        final var row = rows.get(0);
        final var rowId = row.get("id");
        assertEquals(201, created.statusCode());
        assertEquals("active", this.field(created, "status"));
        assertEquals("production", this.field(created, "kind"));
        assertEquals(200, suspended.statusCode());
        assertEquals("suspended", this.field(suspended, "status"));
        assertEquals("Shire", this.field(renamed, "name"));
        assertEquals("suspended", this.field(renamed, "status"));
        assertEquals(200, listed.statusCode());
        assertEquals(id, rowId.asText());
    }

    @Test
    void anAdminManagesAdmins() throws Exception {
        final var created = this.send("POST", "/system/admins", "{\"name\":\"Second\"}", this.admin);
        final var id = this.field(created, "id");
        final var disabled = this.send("PATCH", "/system/admins/" + id, "{\"status\":\"disabled\"}", this.admin);
        final var listed = this.send("GET", "/system/admins", null, this.admin);
        final var refused = this.send("GET", "/system/tenants", null, UUID.fromString(id));
        assertEquals(201, created.statusCode());
        assertEquals("active", this.field(created, "status"));
        assertEquals("disabled", this.field(disabled, "status"));
        assertEquals(2, this.count(listed, "admins"));
        assertEquals(400, refused.statusCode());
        assertEquals("not a system admin", this.error(refused));
    }

    @Test
    void requestsAreChecked() throws Exception {
        final var stranger = this.send("POST", "/system/tenants", COUNTY, UUID.randomUUID());
        final var anonymous = this.send("GET", "/system/admins", null, null);
        final var badId = this.send("PATCH", "/system/tenants/nope", "{}", this.admin);
        final var badStatus = this.send("PATCH", "/system/admins/" + this.admin, "{\"status\":\"gone\"}", this.admin);
        final var noStatus = this.send("PATCH", "/system/admins/" + this.admin, "{}", this.admin);
        final var badJson = this.send("POST", "/system/admins", "{", this.admin);
        final var nullJson = this.send("POST", "/system/admins", "null", this.admin);
        final var wrongMethod = this.send("DELETE", "/system/tenants", null, this.admin);
        final var wrongAdminMethod = this.send("GET", "/system/admins/" + this.admin, null, this.admin);
        final var unknown = this.send("GET", "/system/other", null, this.admin);
        final var badKind = this.send("POST", "/system/tenants", BAD_KIND, this.admin);
        assertEquals("not a system admin", this.error(stranger));
        assertEquals("SureVote-Actor-Id is required", this.error(anonymous));
        assertEquals("invalid id", this.error(badId));
        assertEquals("Unknown system admin status: gone", this.error(badStatus));
        assertEquals("system admin status is required", this.error(noStatus));
        assertEquals("invalid json", this.error(badJson));
        assertEquals("invalid json", this.error(nullJson));
        assertEquals(404, wrongMethod.statusCode());
        assertEquals(404, wrongAdminMethod.statusCode());
        assertEquals(404, unknown.statusCode());
        assertEquals("Unknown tenant kind: nope", this.error(badKind));
    }

    private HttpResponse<String> send(
        final String method,
        final String path,
        final String body,
        final UUID actor
    ) throws Exception {
        final var builder = HttpRequest.newBuilder();
        builder.uri(URI.create(this.base + path));
        builder.header("Content-Type", "application/json");
        this.addActor(builder, actor);
        builder.method(method, this.publisher(body));
        return this.client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void addActor(final HttpRequest.Builder builder, final UUID actor) {
        if (actor != null) {
            builder.header("SureVote-Actor-Id", actor.toString());
        }
    }

    private HttpRequest.BodyPublisher publisher(final String body) {
        if (body == null) {
            return HttpRequest.BodyPublishers.noBody();
        }
        else {
            return HttpRequest.BodyPublishers.ofString(body);
        }
    }

    private JsonNode tree(final HttpResponse<String> response) throws Exception {
        return this.json.readTree(response.body());
    }

    private String error(final HttpResponse<String> response) throws Exception {
        return this.field(response, "error");
    }

    private String field(final HttpResponse<String> response, final String name) throws Exception {
        final var node = this.list(response, name);
        return node.asText();
    }

    private int count(final HttpResponse<String> response, final String name) throws Exception {
        final var node = this.list(response, name);
        return node.size();
    }

    private JsonNode list(final HttpResponse<String> response, final String name) throws Exception {
        final var tree = this.tree(response);
        return tree.get(name);
    }
}
