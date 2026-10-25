package org.sure_vote.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sure_vote.api.ApiServer;
import org.sure_vote.repository.JdbcSureVoteRepository;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresReaderHttpTest {
    private static final String ROLE = "surevote_public";

    private static final String SECRET = "reader";

    private static PostgreSQLContainer<?> postgres;

    private static JdbcSureVoteRepository owner;

    private static JdbcSureVoteRepository reader;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    static void start() throws SQLException {
        final var container = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres = container;
        container.start();
        PostgresReaderHttpTest.open(container);
    }

    private static void open(final PostgreSQLContainer<?> container) throws SQLException {
        final var url = container.getJdbcUrl();
        final var user = container.getUsername();
        final var password = container.getPassword();
        JdbcSureVoteRepository.migrate(url, user, password);
        owner = JdbcSureVoteRepository.open(url, user, password);
        PostgresReaderHttpTest.execute(url, user, password, "create role " + ROLE + " login password '" + SECRET + "'");
        JdbcSureVoteRepository.grantReader(url, user, password, ROLE);
        reader = JdbcSureVoteRepository.open(url, ROLE, SECRET);
    }

    @AfterAll
    static void stop() {
        final var container = postgres;
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void publicReadsWorkThroughTheReadOnlyRole() throws Exception {
        final var world = SampleElection.fptpOn(owner);
        final var ballot = world.castOne(world.slotA, "Y");
        final var code = world.codes.get(ballot.getId());
        final var server = ApiServer.start(new SureVote(owner), 0);
        final var base = "http://127.0.0.1:" + server.port() + "/elections/" + world.electionId;
        server.withPublicReader(new SureVote(reader));
        try {
            this.assertReadable(base, code);
        }
        finally {
            server.close();
        }
    }

    @Test
    void theReadOnlyRoleSeesNoVoterRowsAndWritesNothing() {
        final var url = postgres.getJdbcUrl();
        final var refused = List.of(
            "select count(*) from person",
            "select count(*) from ballot_issuance",
            "select count(*) from ballot",
            "select count(*) from session",
            "select count(*) from held_key",
            "select count(*) from ballot_dispute",
            "insert into ballot_dispute (id) values (gen_random_uuid())",
            "update election set name = 'x'"
        );
        for (final var sql : refused) {
            assertThrows(IllegalStateException.class, () -> PostgresReaderHttpTest.execute(url, ROLE, SECRET, sql));
        }
        assertThrows(IllegalArgumentException.class, () -> JdbcSureVoteRepository.grantReader("", "", "", "Bad;"));
    }

    private void assertReadable(final String base, final String code) throws Exception {
        final var paths = List.of(
            "",
            "/ballots?code=" + code,
            "/heads",
            "/validators",
            "/validations",
            "/restore-incidents"
        );
        final var found = this.body(base + "/ballots?code=" + code);
        for (final var path : paths) {
            final var response = this.get(base + path);
            assertEquals(200, response.statusCode(), path + " " + response.body());
        }
        assertTrue(found.contains("\"Y\""));
    }

    private String body(final String url) throws Exception {
        final var response = this.get(url);
        return response.body();
    }

    private HttpResponse<String> get(final String url) throws Exception {
        final var builder = HttpRequest.newBuilder(URI.create(url));
        final var request = builder.build();
        return this.client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static Integer execute(final String url, final String user, final String password, final String sql) {
        try {
            final var connection = DriverManager.getConnection(url, user, password);
            final var statement = connection.createStatement();
            try {
                statement.execute(sql);
                return 0;
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
}
