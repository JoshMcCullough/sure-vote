package org.sure_vote.api;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import org.sure_vote.entity.SystemAdmin;
import org.sure_vote.repository.InMemorySureVoteRepository;
import org.sure_vote.repository.JdbcSureVoteRepository;
import org.sure_vote.service.PasskeySettings;
import org.sure_vote.service.SureVote;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final var app = application();
        final var port = port();
        final var root = staticRoot();
        final var server = ApiServer.start(app, port, root);
        final var bound = server.port();
        System.out.println("SureVote listening on " + bound);
    }

    private static SureVote application() {
        final var url = System.getenv("SUREVOTE_JDBC_URL");
        if (url == null || url.isBlank()) {
            return memory();
        }
        else {
            return database(url);
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
        final var rpId = or(text("SUREVOTE_RP_ID"), "localhost");
        final var origins = text("SUREVOTE_ORIGINS");
        app.configurePasskeys(settings(rpId, origins));
        return developer(app);
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
        if ("true".equals(text("SUREVOTE_DEV_SIGN_IN"))) {
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
            final var name = or(text("SUREVOTE_DEMO_TENANT_NAME"), "SureVote demo");
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

    private static Path staticRoot() {
        final var configured = System.getenv("SUREVOTE_STATIC");
        final var path = root(configured);
        final var index = path.resolve("index.html");
        if (Files.isRegularFile(index)) {
            return path;
        }
        else {
            return null;
        }
    }

    private static Path root(final String configured) {
        if (configured == null || configured.isBlank()) {
            return Path.of("ui/dist");
        }
        else {
            return Path.of(configured);
        }
    }
}
