package org.sure_vote.api;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.sure_vote.entity.SystemAdmin;
import org.sure_vote.repository.InMemorySureVoteRepository;
import org.sure_vote.repository.JdbcSureVoteRepository;
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
        return provisioned(bootstrapped(new SureVote(repository)));
    }

    private static SureVote database(final String url) {
        final var user = text("SUREVOTE_JDBC_USER");
        final var password = text("SUREVOTE_JDBC_PASSWORD");
        final var repository = open(url, user, password);
        return provisioned(bootstrapped(new SureVote(repository)));
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
            app.bootstrapSystemAdmin(admin);
            return app;
        }
    }

    private static JdbcSureVoteRepository open(final String url, final String user, final String password) {
        JdbcSureVoteRepository.migrate(url, user, password);
        return JdbcSureVoteRepository.open(url, user, password);
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
