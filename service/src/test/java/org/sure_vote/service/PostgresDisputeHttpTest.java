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
import org.sure_vote.api.Pages;
import org.sure_vote.api.Surface;
import org.sure_vote.repository.JdbcSureVoteRepository;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The public surface as deployed: lookups through the reader role, disputes through the dispute role. */
class PostgresDisputeHttpTest {
    private static final String READER = "surevote_public";

    private static final String DISPUTE = "surevote_dispute";

    private static final String SECRET = "dispute";

    private static PostgreSQLContainer<?> postgres;

    private static JdbcSureVoteRepository owner;

    private static JdbcSureVoteRepository reader;

    private static JdbcSureVoteRepository disputes;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    static void start() throws SQLException {
        final var container = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres = container;
        container.start();
        PostgresDisputeHttpTest.open(container);
    }

    private static void open(final PostgreSQLContainer<?> container) {
        final var url = container.getJdbcUrl();
        final var user = container.getUsername();
        final var password = container.getPassword();
        JdbcSureVoteRepository.migrate(url, user, password);
        owner = JdbcSureVoteRepository.open(url, user, password);
        execute(url, user, password, "create role " + READER + " login password '" + SECRET + "'");
        execute(url, user, password, "create role " + DISPUTE + " login password '" + SECRET + "'");
        JdbcSureVoteRepository.grantReader(url, user, password, READER);
        JdbcSureVoteRepository.grantDispute(url, user, password, DISPUTE);
        reader = JdbcSureVoteRepository.open(url, READER, SECRET);
        disputes = JdbcSureVoteRepository.open(url, DISPUTE, SECRET);
    }

    @AfterAll
    static void stop() {
        final var container = postgres;
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void theDisputeRoleFilesAndReadsBackOnlyTheVotersOwnDispute() throws Exception {
        final var world = SampleElection.fptpOn(owner);
        final var mine = world.castOne(world.slotA, "Y");
        final var theirs = world.castOne(world.slotA, "Y");
        final var code = world.codes.get(mine.getId());
        final var other = world.codes.get(theirs.getId());
        final var server = ApiServer.start(new SureVote(disputes), 0, Pages.none(), Surface.PUBLIC);
        final var base = "http://127.0.0.1:" + server.port() + "/elections/" + world.electionId;
        server.withPublicReader(new SureVote(reader));
        try {
            final var filed = this.post(base + "/disputes", this.claim(code, "My vote is wrong"));
            final var theirsFiled = this.post(base + "/disputes", this.claim(other, "Someone else"));
            final var listed = this.post(base + "/disputes/lookup", "{\"code\":\"" + code + "\"}");
            final var count = this.get(base + "/disputes/count");
            final var looked = this.get(base + "/ballots?code=" + code);
            final var listedBody = listed.body();
            assertEquals(201, filed.statusCode(), filed.body());
            assertEquals(201, theirsFiled.statusCode(), theirsFiled.body());
            assertEquals(200, listed.statusCode(), listed.body());
            assertTrue(listedBody.contains("My vote is wrong"), listedBody);
            assertFalse(listedBody.contains("Someone else"), listedBody);
            assertEquals("{\"count\":2}", count.body());
            assertEquals(200, looked.statusCode(), looked.body());
        }
        finally {
            server.close();
        }
    }

    @Test
    void theDisputeRoleCannotReadOrChangeAnythingElse() {
        final var url = postgres.getJdbcUrl();
        final var refused = List.of(
            "select statement from ballot_dispute",
            "select * from ballot_dispute",
            "update ballot_dispute set status = 'dismissed'",
            "delete from ballot_dispute",
            "select count(*) from ballot",
            "select count(*) from person",
            "select count(*) from ballot_issuance",
            "select count(*) from session",
            "select count(*) from held_key",
            "insert into ballot (id) values (gen_random_uuid())",
            "insert into vote_chain_entry (id) values (gen_random_uuid())",
            "update election set name = 'x'"
        );
        for (final var sql : refused) {
            assertThrows(IllegalStateException.class, () -> execute(url, DISPUTE, SECRET, sql), sql);
        }
        assertThrows(IllegalArgumentException.class, () -> JdbcSureVoteRepository.grantDispute("", "", "", "Bad;"));
    }

    @Test
    void aDisputeIsAlwaysFiledOpen() {
        final var world = SampleElection.fptpOn(owner);
        final var ballot = world.castOne(world.slotA, "Y");
        final var url = postgres.getJdbcUrl();
        final var dismissed = this.insert(world, ballot.getId(), "dismissed");
        final var open = this.insert(world, ballot.getId(), "open");
        final var failure = assertThrows(IllegalStateException.class, () -> execute(url, DISPUTE, SECRET, dismissed));
        final var cause = failure.getCause();
        final var message = cause.getMessage();
        assertTrue(message.contains("row-level security"), message);
        assertEquals(0, execute(url, DISPUTE, SECRET, open));
    }

    private String insert(final SampleElection world, final java.util.UUID ballotId, final String status) {
        return "begin; select set_config('app.tenant_id', '" + world.tenantId + "', true); "
            + "insert into ballot_dispute (id, tenant_id, ballot_id, status) values (gen_random_uuid(), '"
            + world.tenantId + "', '" + ballotId + "', '" + status + "'); commit;";
    }

    private String claim(final String code, final String statement) {
        return "{\"code\":\"" + code + "\",\"statement\":\"" + statement + "\"}";
    }

    private HttpResponse<String> post(final String url, final String body) throws Exception {
        final var builder = HttpRequest.newBuilder(URI.create(url));
        builder.header("Content-Type", "application/json");
        builder.POST(HttpRequest.BodyPublishers.ofString(body));
        return this.client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
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
