package org.sure_vote.api;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.sure_vote.entity.SystemAdmin;
import org.sure_vote.repository.InMemorySureVoteRepository;
import org.sure_vote.repository.JdbcSureVoteRepository;
import org.sure_vote.service.InMemoryReceivedQueue;
import org.sure_vote.service.PasskeySettings;
import org.sure_vote.service.SqsReceivedQueue;
import org.sure_vote.service.SureVote;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final var surface = Startup.checked(Surface.parse(text("SUREVOTE_SURFACE")), System.getenv());
        final var app = application(surface);
        final var port = port();
        final var pages = new Pages(staticRoot("SUREVOTE_STATIC", "ui/apps/workspace/dist"),
            staticRoot("SUREVOTE_STATIC_PUBLIC", "ui/apps/public/dist"));
        final var reader = locked(surface, app, reader(app));
        final var server = ApiServer.start(app, port, pages, surface);
        final var served = server.withPublicReader(reader);
        final var linked = served.withLookupUrl(text("SUREVOTE_LOOKUP_URL"));
        final var bound = linked.port();
        System.out.println("SureVote listening on " + bound + " as the " + surface.wire() + " surface");
    }

    private static SureVote locked(final Surface surface, final SureVote app, final SureVote reader) {
        final var id = text("SUREVOTE_DEMO_TENANT_ID");
        if (surface != Surface.DEMO) {
            return reader;
        }
        else {
            final var tenants = Set.of(UUID.fromString(id));
            app.lockTenants(tenants);
            reader.lockTenants(tenants);
            return reader;
        }
    }

    private static SureVote application(final Surface surface) {
        final var url = text("SUREVOTE_JDBC_URL");
        if (url.isBlank()) {
            return memory();
        }
        else if (surface == Surface.PUBLIC) {
            return disputes(url);
        }
        else {
            return database(url);
        }
    }

    /** The public surface writes only disputes, through the dispute role. It runs no jobs and provisions nothing. */
    private static SureVote disputes(final String url) {
        final var role = prepared(url, text("SUREVOTE_JDBC_DISPUTE_USER"));
        final var repository = JdbcSureVoteRepository.open(url, role, text("SUREVOTE_JDBC_DISPUTE_PASSWORD"));
        return origins(new SureVote(repository));
    }

    /** The allowed origins for unsafe requests come from the passkey settings, so every surface sets them. */
    private static SureVote origins(final SureVote app) {
        final var rpId = or(text("SUREVOTE_RP_ID"), "localhost");
        app.configurePasskeys(settings(rpId, text("SUREVOTE_ORIGINS")));
        return app;
    }

    /** With owner credentials the process migrates and grants first; without them a separate job has done it. */
    private static String prepared(final String url, final String role) {
        final var owner = text("SUREVOTE_JDBC_USER");
        final var password = text("SUREVOTE_JDBC_PASSWORD");
        if (owner.isBlank()) {
            return role;
        }
        else {
            JdbcSureVoteRepository.migrate(url, owner, password);
            JdbcSureVoteRepository.grantDispute(url, owner, password, role);
            return role;
        }
    }

    private static SureVote reader(final SureVote app) {
        final var url = text("SUREVOTE_JDBC_URL");
        final var role = text("SUREVOTE_JDBC_PUBLIC_USER");
        final var owner = text("SUREVOTE_JDBC_USER");
        if (url.isBlank() || role.isBlank()) {
            return app;
        }
        else {
            granted(url, owner, role);
            return new SureVote(JdbcSureVoteRepository.open(url, role, text("SUREVOTE_JDBC_PUBLIC_PASSWORD")));
        }
    }

    private static void granted(final String url, final String owner, final String role) {
        if (!owner.isBlank()) {
            JdbcSureVoteRepository.grantReader(url, owner, text("SUREVOTE_JDBC_PASSWORD"), role);
        }
    }

    private static SureVote memory() {
        final var repository = new InMemorySureVoteRepository();
        return configured(provisioned(bootstrapped(new SureVote(repository))));
    }

    private static SureVote database(final String url) {
        final var user = text("SUREVOTE_JDBC_USER");
        final var password = text("SUREVOTE_JDBC_PASSWORD");
        final var repository = open(url, user, password);
        return configured(provisioned(bootstrapped(new SureVote(repository))));
    }

    private static SureVote configured(final SureVote app) {
        return scheduled(queued(developer(origins(app))));
    }

    private static SureVote queued(final SureVote app) {
        final var url = text("SUREVOTE_RECEIVED_QUEUE_URL");
        final var size = Integer.parseInt(or(text("SUREVOTE_RECEIVED_BATCH_SIZE"), "25"));
        if (url.isBlank()) {
            app.configureReceivedQueue(new InMemoryReceivedQueue(), size);
            return app;
        }
        else {
            final var region = or(text("SUREVOTE_RECEIVED_QUEUE_REGION"), "us-east-1");
            app.configureReceivedQueue(SqsReceivedQueue.create(url, region), size);
            return app;
        }
    }

    private static SureVote scheduled(final SureVote app) {
        final var seconds = Long.parseLong(or(text("SUREVOTE_RECEIVED_FLUSH_SECONDS"), "300"));
        final var threads = Thread.ofVirtual();
        final var executor = Executors.newSingleThreadScheduledExecutor(threads.factory());
        executor.scheduleWithFixedDelay(() -> flushed(app), seconds, seconds, TimeUnit.SECONDS);
        return app;
    }

    private static void flushed(final SureVote app) {
        try {
            app.flushReceivedBatch();
            app.pruneNonces();
        }
        catch (final RuntimeException failure) {
            System.out.println("SureVote could not flush received marks or prune nonces. "
                + "It tries again on the next run.");
        }
    }

    private static PasskeySettings settings(final String rpId, final String origins) {
        final var local = PasskeySettings.local();
        if (origins.isBlank()) {
            return new PasskeySettings(rpId, local.rpName(), local.origins());
        }
        else {
            return new PasskeySettings(rpId, local.rpName(), Set.of(origins.split(",")));
        }
    }

    private static SureVote developer(final SureVote app) {
        final var wanted = "true".equals(text("SUREVOTE_DEV_SIGN_IN"));
        if (wanted) {
            System.out.println("SureVote dev sign-in is enabled. Do not enable it in production.");
            app.enableDevSignIn();
            return app;
        }
        else {
            return app;
        }
    }

    private static SureVote provisioned(final SureVote app) {
        final var id = text("SUREVOTE_DEMO_TENANT_ID");
        if (id.isBlank()) {
            return app;
        }
        else {
            final var name = or(text("SUREVOTE_DEMO_TENANT_NAME"), "SureVote Demo");
            final var region = or(text("SUREVOTE_DEMO_TENANT_REGION"), "us-east");
            app.provisionDemoTenant(UUID.fromString(id), name, region);
            return app;
        }
    }

    private static String or(final String value, final String fallback) {
        if (value.isBlank()) {
            return fallback;
        }
        else {
            return value;
        }
    }

    private static SureVote bootstrapped(final SureVote app) {
        final var id = text("SUREVOTE_SYSTEM_ADMIN_ID");
        if (id.isBlank()) {
            return app;
        }
        else {
            final var admin = new SystemAdmin();
            admin.setId(UUID.fromString(id));
            admin.setName(text("SUREVOTE_SYSTEM_ADMIN_NAME"));
            enrolled(app, admin, text("SUREVOTE_SYSTEM_ADMIN_ENROLLMENT_TOKEN"));
            return app;
        }
    }

    private static void enrolled(final SureVote app, final SystemAdmin admin, final String token) {
        if (token.isBlank()) {
            app.bootstrapSystemAdmin(admin);
        }
        else {
            app.bootstrapSystemAdmin(admin, token);
        }
    }

    private static JdbcSureVoteRepository open(final String url, final String user, final String password) {
        final var writer = text("SUREVOTE_JDBC_APP_USER");
        JdbcSureVoteRepository.migrate(url, user, password);
        if (writer.isBlank()) {
            System.out.println("SureVote connects as the table owner, which bypasses row-level security. "
                + "Set SUREVOTE_JDBC_APP_USER outside local development.");
            return JdbcSureVoteRepository.open(url, user, password);
        }
        else {
            JdbcSureVoteRepository.grantWriter(url, user, password, writer);
            return JdbcSureVoteRepository.open(url, writer, text("SUREVOTE_JDBC_APP_PASSWORD"));
        }
    }

    private static String text(final String name) {
        final var value = System.getenv(name);
        if (value == null) {
            return "";
        }
        else {
            return value;
        }
    }

    private static int port() {
        final var value = System.getenv("SUREVOTE_PORT");
        if (value == null || value.isBlank()) {
            return 8080;
        }
        else {
            return Integer.parseInt(value);
        }
    }

    private static Path staticRoot(final String variable, final String fallback) {
        final var configured = System.getenv(variable);
        final var path = root(configured, fallback);
        final var index = path.resolve("index.html");
        if (Files.isRegularFile(index)) {
            return path;
        }
        else {
            return null;
        }
    }

    private static Path root(final String configured, final String fallback) {
        if (configured == null || configured.isBlank()) {
            return Path.of(fallback);
        }
        else {
            return Path.of(configured);
        }
    }
}
