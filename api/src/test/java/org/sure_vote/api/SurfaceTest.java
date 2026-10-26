package org.sure_vote.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sure_vote.entity.PrincipalKind;
import org.sure_vote.entity.SystemAdmin;
import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.TenantKind;
import org.sure_vote.entity.TenantStatus;
import org.sure_vote.repository.InMemorySureVoteRepository;
import org.sure_vote.service.Principal;
import org.sure_vote.service.SureVote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SurfaceTest {
    private static final String PERSON = "{\"firstName\":\"Bea\",\"lastName\":\"Two\"}";

    private static final List<Probe> PROBES = List.of(
        new Probe(RouteGroup.HEALTH, "GET", "/health", null),
        new Probe(RouteGroup.PUBLIC, "GET", "/elections/nope", null),
        new Probe(RouteGroup.PUBLIC, "POST", "/lookup", "{"),
        new Probe(RouteGroup.PUBLIC, "POST", "/elections/nope/lookup", "{}"),
        new Probe(RouteGroup.PUBLIC, "GET", "/elections/nope/ballots", null),
        new Probe(RouteGroup.PUBLIC, "GET", "/elections/nope/disputes/count", null),
        new Probe(RouteGroup.PUBLIC, "POST", "/elections/nope/disputes/lookup", "{}"),
        new Probe(RouteGroup.PUBLIC, "POST", "/elections/nope/disputes", "{}"),
        new Probe(RouteGroup.PUBLIC, "GET", "/elections/nope/heads", null),
        new Probe(RouteGroup.PUBLIC, "GET", "/elections/nope/validators", null),
        new Probe(RouteGroup.PUBLIC, "GET", "/elections/nope/validations", null),
        new Probe(RouteGroup.PUBLIC, "GET", "/elections/nope/close-bundle", null),
        new Probe(RouteGroup.PUBLIC, "GET", "/elections/nope/restore-incidents", null),
        new Probe(RouteGroup.OFFICIAL, "POST", "/persons", PERSON),
        new Probe(RouteGroup.OFFICIAL, "POST", "/elections", "{}"),
        new Probe(RouteGroup.OFFICIAL, "POST", "/elections/nope/activate", "{}"),
        new Probe(RouteGroup.OFFICIAL, "GET", "/elections/nope/disputes", null),
        new Probe(RouteGroup.OFFICIAL, "POST", "/elections/nope/definition", null),
        new Probe(RouteGroup.OFFICIAL, "POST", "/localities", "{}"),
        new Probe(RouteGroup.OFFICIAL, "GET", "/workspace", null),
        new Probe(RouteGroup.MACHINE, "POST", "/ballots", "{}"),
        new Probe(RouteGroup.MACHINE, "POST", "/elections/nope/validations", "{}"),
        new Probe(RouteGroup.SESSION, "GET", "/auth/config", null),
        new Probe(RouteGroup.SESSION, "GET", "/auth/session", null),
        new Probe(RouteGroup.SESSION, "POST", "/auth/sign-out", null),
        new Probe(RouteGroup.SIGN_IN, "POST", "/auth/sign-in/start", null),
        new Probe(RouteGroup.SIGN_IN, "POST", "/auth/dev/sign-in", "{}"),
        new Probe(RouteGroup.SYSTEM, "GET", "/system/tenants", null),
        new Probe(RouteGroup.SYSTEM, "GET", "/system/admins", null),
        new Probe(RouteGroup.SHOWCASE, "GET", "/showcase/tenant", null),
        new Probe(RouteGroup.SHOWCASE, "POST", "/showcase/session", null),
        new Probe(RouteGroup.SHOWCASE, "POST", "/showcase/elections", "{}")
    );

    private final List<ApiServer> servers = new ArrayList<>();
    private final HttpClient client = HttpClient.newHttpClient();
    private SureVote app;
    private UUID demo;
    private UUID county;
    private String countyOfficial;
    private String demoOfficial;

    @BeforeEach
    void setUp() {
        final var created = new SureVote(new InMemorySureVoteRepository());
        final var id = UUID.randomUUID();
        final var host = demoHost(created, id);
        this.app = created;
        this.demo = id;
        this.county = county(this.app, host);
        this.countyOfficial = official(this.app, host, this.county);
        this.demoOfficial = official(this.app, host, this.demo);
    }

    @AfterEach
    void tearDown() {
        this.servers.forEach(ApiServer::close);
    }

    @Test
    void eachSurfaceServesOnlyItsRoutes() throws Exception {
        for (final var surface : Surface.values()) {
            final var base = this.start(surface);
            for (final var probe : PROBES) {
                final var response = this.send(base, probe.method(), probe.path(), probe.body(), null);
                final var label = surface + " " + probe.method() + " " + probe.path() + " " + response.body();
                if (surface.serves(probe.group())) {
                    assertNotEquals("{\"error\":\"not found\"}", response.body(), label);
                }
                else {
                    assertEquals(404, response.statusCode(), label);
                    assertEquals("{\"error\":\"not found\"}", response.body(), label);
                }
            }
        }
    }

    @Test
    void theSurfacesMatchTheTopology() {
        assertEquals(Set.of(RouteGroup.HEALTH, RouteGroup.PUBLIC), this.served(Surface.PUBLIC));
        assertEquals(
            Set.of(
                RouteGroup.HEALTH,
                RouteGroup.OFFICIAL,
                RouteGroup.MACHINE,
                RouteGroup.SESSION,
                RouteGroup.SIGN_IN,
                RouteGroup.SYSTEM
            ),
            this.served(Surface.WORKSPACE)
        );
        assertEquals(
            Set.of(RouteGroup.HEALTH, RouteGroup.PUBLIC, RouteGroup.OFFICIAL, RouteGroup.SESSION, RouteGroup.SHOWCASE),
            this.served(Surface.DEMO)
        );
        assertEquals(Surface.ALL, Surface.parse(""));
        assertEquals(Surface.DEMO, Surface.parse("demo"));
    }

    @Test
    void theWorkspaceRefusesVisitors() throws Exception {
        final var all = this.start(Surface.ALL);
        final var visitor = this.visitor(all);
        final var base = this.start(Surface.WORKSPACE);
        final var refused = this.send(base, "GET", "/workspace", null, visitor);
        final var session = this.send(base, "GET", "/auth/session", null, visitor);
        final var official = this.send(base, "GET", "/workspace", null, this.countyOfficial);
        assertEquals(401, refused.statusCode());
        assertEquals(401, session.statusCode());
        assertEquals(200, official.statusCode());
    }

    @Test
    void theDemoAcceptsOnlyDemoVisitors() throws Exception {
        final var base = this.locked(Surface.DEMO);
        final var visitor = this.visitor(base);
        final var person = this.send(base, "POST", "/persons", PERSON, visitor);
        final var county = this.send(base, "POST", "/persons", PERSON, this.countyOfficial);
        final var demoOfficial = this.send(base, "POST", "/persons", PERSON, this.demoOfficial);
        final var system = this.send(base, "GET", "/system/tenants", null, this.countyOfficial);
        assertEquals(201, person.statusCode());
        assertEquals(404, county.statusCode());
        assertEquals(401, demoOfficial.statusCode());
        assertEquals(404, system.statusCode());
    }

    @Test
    void theTenantLockRefusesOtherTenantsBehindAnyRoute() throws Exception {
        final var base = this.locked(Surface.ALL);
        final var county = this.send(base, "POST", "/persons", PERSON, this.countyOfficial);
        final var demoOfficial = this.send(base, "POST", "/persons", PERSON, this.demoOfficial);
        final var workspace = this.send(base, "GET", "/workspace", null, this.countyOfficial);
        assertEquals(404, county.statusCode());
        assertEquals("{\"error\":\"not found\"}", county.body());
        assertEquals(201, demoOfficial.statusCode());
        assertEquals(404, workspace.statusCode());
    }

    private Set<RouteGroup> served(final Surface surface) {
        final var groups = new HashSet<RouteGroup>();
        for (final var group : RouteGroup.values()) {
            if (surface.serves(group)) {
                groups.add(group);
            }
        }
        return groups;
    }

    private String locked(final Surface surface) {
        this.app.lockTenants(Set.of(this.demo));
        return this.start(surface);
    }

    private String start(final Surface surface) {
        final var server = ApiServer.start(this.app, 0, null, surface);
        this.servers.add(server);
        return "http://127.0.0.1:" + server.port();
    }

    private String visitor(final String base) throws Exception {
        final var response = this.send(base, "POST", "/showcase/session", null, null);
        final var headers = response.headers();
        final var header = headers.firstValue("Set-Cookie");
        final var value = header.orElseThrow();
        return value.substring(0, value.indexOf(';'));
    }

    private HttpResponse<String> send(
        final String base,
        final String method,
        final String path,
        final String body,
        final String cookie
    ) throws Exception {
        final var builder = HttpRequest.newBuilder();
        final var publisher = body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body);
        builder.uri(URI.create(base + path));
        builder.header("Content-Type", "application/json");
        builder.method(method, publisher);
        withCookie(builder, cookie);
        return this.client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void withCookie(final HttpRequest.Builder builder, final String cookie) {
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
    }

    private static UUID county(final SureVote app, final Principal host) {
        final var saved = app.createSystemTenant(countyRow(), host.id());
        return saved.getId();
    }

    private static Tenant countyRow() {
        final var tenant = new Tenant();
        tenant.setName("County");
        tenant.setKind(TenantKind.PRODUCTION);
        tenant.setResidencyRegion("us-east");
        tenant.setStatus(TenantStatus.ACTIVE);
        return tenant;
    }

    private static String official(final SureVote app, final Principal host, final UUID tenantId) {
        final var created = app.createOfficial(tenantId, "Clerk", host);
        final var row = created.official();
        return "surevote_session=" + app.devSignIn(PrincipalKind.OFFICIAL, row.getId(), tenantId);
    }

    private static Principal demoHost(final SureVote app, final UUID demoId) {
        app.provisionDemoTenant(demoId, "Demo", "us-east");
        return host(app);
    }

    private static Principal host(final SureVote app) {
        final var admin = new SystemAdmin();
        admin.setId(UUID.randomUUID());
        admin.setName("Host");
        app.bootstrapSystemAdmin(admin);
        app.enableDevSignIn();
        return app.session(app.devSignIn(PrincipalKind.SYSTEM_ADMIN, admin.getId(), null));
    }

    private record Probe(RouteGroup group, String method, String path, String body) {
    }
}
