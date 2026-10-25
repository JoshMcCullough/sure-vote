package org.sure_vote.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.sure_vote.api.ApiServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpSignInTest {
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private String base;

    @Test
    void anAdminAddsAnOfficialWhoSignsInWithAPasskey() throws Exception {
        final var world = SignInRulesTest.passkeys(SampleElection.showcase());
        world.admin();
        this.use(world, () -> this.officialFlow(world));
    }

    @Test
    void requestsNeedASessionAndAnAllowedOrigin() throws Exception {
        final var world = SignInRulesTest.passkeys(SampleElection.showcase());
        world.admin();
        this.use(world, () -> this.guarded(world));
    }

    @Test
    void devSignInStaysOffUnlessConfigured() throws Exception {
        final var world = SampleElection.showcase();
        final var admin = world.admin();
        this.use(world, () -> this.devOff(admin.toString()));
    }

    @Test
    void aVisitorGetsADemoSessionButCannotManageOfficials() throws Exception {
        final var world = SignInRulesTest.passkeys(SampleElection.showcase());
        world.app.provisionDemoTenant(world.tenantId, "Demo", "us-east");
        this.use(world, this::visitor);
    }

    private void officialFlow(final SampleElection world) throws Exception {
        final var admin = this.devAdmin(world);
        final var officials = "/system/tenants/" + world.tenantId + "/officials";
        final var created = this.send("POST", officials, "{\"name\":\"Clerk\"}", admin);
        final var token = this.field(created.body(), "enrollmentToken");
        final var device = new SoftwareAuthenticator(SignInRulesTest.ORIGIN);
        final var official = this.register(token, device);
        final var session = this.send("GET", "/auth/session", null, official);
        final var deputy = this.send("POST", "/officials", "{\"name\":\"Deputy\"}", official);
        final var deputyId = this.field(deputy.body(), "/official/id");
        final var disabled = this.send("PATCH", "/officials/" + deputyId, "{\"status\":\"disabled\"}", official);
        final var listed = this.send("GET", "/officials", null, official);
        final var again = this.signIn(device);
        final var signedOut = this.send("POST", "/auth/sign-out", null, again);
        final var after = this.send("GET", "/auth/session", null, again);
        final var cleared = this.cookieHeader(signedOut);
        assertEquals(201, created.statusCode());
        assertEquals("official", this.field(session.body(), "kind"));
        assertEquals(world.tenantId.toString(), this.field(session.body(), "tenantId"));
        assertEquals(201, deputy.statusCode());
        assertEquals("disabled", this.field(disabled.body(), "status"));
        assertEquals(2, this.count(listed.body(), "/officials"));
        assertEquals(204, signedOut.statusCode());
        assertTrue(cleared.contains("Max-Age=0"));
        assertEquals(401, after.statusCode());
    }

    private void guarded(final SampleElection world) throws Exception {
        final var anonymous = this.send("GET", "/auth/session", null, null);
        final var config = this.send("GET", "/auth/config", null, null);
        final var signIn = this.adminJson(world);
        final var dev = this.dev(signIn, null);
        final var cookie = this.cookieHeader(dev);
        final var admin = this.session(dev);
        final var foreign = this.dev(signIn, "https://evil.test");
        final var allowed = this.dev(signIn, SignInRulesTest.ORIGIN);
        final var ceremony = "{\"ceremonyId\":\"" + world.tenantId + "\"}";
        final var noCredential = this.send("POST", "/auth/sign-in/finish", ceremony, null);
        final var badKind = this.dev("{\"kind\":\"wizard\"}", null);
        final var badEnrollment = this.send("POST", "/auth/enrollments/start", "{\"token\":\"nope\"}", null);
        final var unknown = this.send("GET", "/officials/x/y/z", null, admin);
        final var adminOfficials = this.send("GET", "/officials", null, admin);
        assertEquals(401, anonymous.statusCode());
        assertEquals("sign-in required", this.field(anonymous.body(), "error"));
        assertEquals("true", this.field(config.body(), "devSignIn"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Strict"));
        assertEquals(403, foreign.statusCode());
        assertEquals("origin not allowed", this.field(foreign.body(), "error"));
        assertEquals(200, allowed.statusCode());
        assertEquals("credential is required", this.field(noCredential.body(), "error"));
        assertEquals(400, badKind.statusCode());
        assertEquals("enrollment token is invalid or used", this.field(badEnrollment.body(), "error"));
        assertEquals(404, unknown.statusCode());
        assertEquals(403, adminOfficials.statusCode());
    }

    private void devOff(final String admin) throws Exception {
        final var refused = this.dev("{\"kind\":\"system-admin\",\"id\":\"" + admin + "\"}", null);
        assertEquals(403, refused.statusCode());
        assertEquals("dev sign-in is disabled", this.field(refused.body(), "error"));
    }

    private void visitor() throws Exception {
        final var started = this.send("POST", "/showcase/session", null, null);
        final var visitor = this.session(started);
        final var listed = this.send("GET", "/officials", null, visitor);
        final var passkey = this.send("POST", "/auth/passkeys/start", null, visitor);
        assertEquals("visitor", this.field(started.body(), "kind"));
        assertEquals(403, listed.statusCode());
        assertEquals("a visitor has no passkey", this.field(passkey.body(), "error"));
    }

    private String devAdmin(final SampleElection world) throws Exception {
        return this.session(this.dev(this.adminJson(world), null));
    }

    private String adminJson(final SampleElection world) {
        return "{\"kind\":\"system-admin\",\"id\":\"" + world.systemAdmin + "\"}";
    }

    private HttpResponse<String> dev(final String body, final String origin) throws Exception {
        final var builder = this.builder("POST", "/auth/dev/sign-in", body, null);
        if (origin != null) {
            builder.header("Origin", origin);
            return this.client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
        else {
            return this.client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private String register(final String token, final SoftwareAuthenticator device) throws Exception {
        final var started = this.send("POST", "/auth/enrollments/start", "{\"token\":\"" + token + "\"}", null);
        final var challenge = this.json.readTree(started.body());
        final var credential = device.register(this.options(challenge));
        return this.session(this.finish("/auth/passkeys/finish", challenge, credential));
    }

    private String signIn(final SoftwareAuthenticator device) throws Exception {
        final var started = this.send("POST", "/auth/sign-in/start", null, null);
        final var challenge = this.json.readTree(started.body());
        final var credential = device.assertion(this.options(challenge));
        return this.session(this.finish("/auth/sign-in/finish", challenge, credential));
    }

    private HttpResponse<String> finish(final String path, final JsonNode challenge, final String credential)
        throws Exception {
        final var ceremony = challenge.get("ceremonyId");
        final var body = "{\"ceremonyId\":\"" + ceremony.asText() + "\",\"credential\":" + credential + "}";
        return this.send("POST", path, body, null);
    }

    private String options(final JsonNode challenge) {
        final var options = challenge.get("publicKey");
        return options.toString();
    }

    private String session(final HttpResponse<String> response) {
        final var header = this.cookieHeader(response);
        return header.substring(0, header.indexOf(';'));
    }

    private String cookieHeader(final HttpResponse<String> response) {
        final var headers = response.headers();
        final var cookie = headers.firstValue("Set-Cookie");
        return cookie.orElseThrow();
    }

    private String field(final String body, final String name) throws Exception {
        final var tree = this.json.readTree(body);
        final var found = tree.at(name.startsWith("/") ? name : "/" + name);
        return found.asText();
    }

    private int count(final String body, final String pointer) throws Exception {
        final var tree = this.json.readTree(body);
        final var found = tree.at(pointer);
        return found.size();
    }

    private HttpResponse<String> send(final String method, final String path, final String body, final String cookie)
        throws Exception {
        final var builder = this.builder(method, path, body, cookie);
        return this.client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder builder(
        final String method,
        final String path,
        final String body,
        final String cookie
    ) {
        final var builder = HttpRequest.newBuilder(URI.create(this.base + path));
        builder.header("Content-Type", "application/json");
        builder.method(method, this.publisher(body));
        if (cookie != null) {
            builder.header("Cookie", cookie);
            return builder;
        }
        else {
            return builder;
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

    private void use(final SampleElection world, final Work work) throws Exception {
        final var server = ApiServer.start(world.app, 0);
        this.base = "http://127.0.0.1:" + server.port();
        try {
            work.run();
        }
        finally {
            server.close();
        }
    }

    private interface Work {
        void run() throws Exception;
    }
}
