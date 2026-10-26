package org.sure_vote.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresRulesTest {
    private static final String BALLOT = "insert into ballot"
        + " (id, tenant_id, election_id, voting_precinct_id, voter_access_hash, timestamp)"
        + " values (?, ?, ?, ?, ?, ?)";

    private static final String VOTE = "insert into vote (id, tenant_id, ballot_id, race_candidate_id, value)"
        + " values (?, ?, ?, ?, ?)";

    private static final String RACE = "insert into race"
        + " (id, tenant_id, election_id, term_id, name, previous_race_id, method, round)"
        + " values (?, ?, ?, ?, 'Mayor', ?, ?, ?)";

    private static final String TERM = "insert into term (id, tenant_id, seat_id, term_start, term_end)"
        + " values (?, ?, ?, ?, ?)";

    private static PostgreSQLContainer<?> postgres;

    private static JdbcSureVoteRepository repository;

    private ParentRows rows;

    @BeforeAll
    static void start() {
        final var container = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres = container;
        container.start();
        PostgresRulesTest.open(container);
    }

    private static void open(final PostgreSQLContainer<?> container) {
        final var url = container.getJdbcUrl();
        final var user = container.getUsername();
        final var password = container.getPassword();
        JdbcSureVoteRepository.migrate(url, user, password);
        repository = JdbcSureVoteRepository.open(url, user, password);
    }

    @AfterAll
    static void stop() {
        final var container = postgres;
        if (container != null) {
            container.stop();
        }
    }

    @BeforeEach
    void setUp() {
        repository.clear();
        this.rows = new ParentRows(repository);
    }

    @Test
    void aBallotIsSealedInsideItsVotingWindow() throws SQLException {
        final var before = Timestamp.from(ParentRows.START.minus(Duration.ofSeconds(1)));
        final var after = Timestamp.from(ParentRows.END.plus(Duration.ofSeconds(1)));
        final var message = "Ballot timestamp is outside the voting window";
        this.refused(BALLOT, message, this.ballotValues("early", before));
        this.refused(BALLOT, message, this.ballotValues("late", after));
        this.refused(BALLOT, message, this.ballotValues("none", null));
        this.run(BALLOT, this.ballotValues("start", Timestamp.from(ParentRows.START)));
        this.run(BALLOT, this.ballotValues("end", Timestamp.from(ParentRows.END)));
    }

    @Test
    void anElectionWindowEndsAfterItStarts() {
        this.refused(
            "update election set voting_start = voting_end + interval '1 second' where id = ?",
            "election_voting_window",
            this.rows.electionId
        );
    }

    @Test
    void aFirstPastThePostRaceTakesOneYVote() throws SQLException {
        final var race = this.rows.race();
        final var first = this.rows.slot(race);
        final var second = this.rows.slot(race);
        final var ballot = this.rows.ballot(this.rows.electionId);
        this.refused(VOTE, "A first-past-the-post vote uses Y", this.vote(ballot, first, "N"));
        this.run(VOTE, this.vote(ballot, first, "Y"));
        this.refused(VOTE, "A first-past-the-post race allows one vote", this.vote(ballot, second, "Y"));
        this.refused(VOTE, "A first-past-the-post race allows one vote", this.vote(ballot, first, "Y"));
    }

    @Test
    void aWriteInNameIsCleanNfcText() throws SQLException {
        final var slot = this.rows.slot(this.rows.race());
        this.run("update race_candidate set write_in = true where id = ?", slot);
        this.refusedName(slot, null, "A write-in name is required");
        this.refusedName(slot, "e\u0301", "A write-in name is Unicode NFC");
        this.refusedName(slot, " Ada", "A write-in name has no extra whitespace");
        this.refusedName(slot, "", "A write-in name is 1 to 80 characters");
        this.refusedName(slot, "a".repeat(81), "A write-in name is 1 to 80 characters");
        this.refusedName(slot, "Ada\u0007", "A write-in name has no control characters");
        this.run(VOTE, this.vote(this.rows.ballot(this.rows.electionId), slot, "Ada Lovelace"));
    }

    @Test
    void aReferendumTakesOneYesOrNoVote() throws SQLException {
        final var race = this.rows.race();
        final var yes = this.rows.slot(race);
        final var no = this.rows.slot(race);
        final var ballot = this.rows.ballot(this.rows.electionId);
        this.run("update race set method = 'referendum' where id = ?", race);
        this.refused(VOTE, "A referendum candidate is yes or no", this.vote(ballot, yes, "Y"));
        this.run("update race_candidate set referendum_choice = 'yes' where id = ?", yes);
        this.run("update race_candidate set referendum_choice = 'no' where id = ?", no);
        this.refused(VOTE, "A referendum vote uses Y", this.vote(ballot, yes, "1"));
        this.run(VOTE, this.vote(ballot, yes, "Y"));
        this.refused(VOTE, "A referendum allows one vote", this.vote(ballot, no, "Y"));
    }

    @Test
    void rankedChoiceRanksAreContiguousFromOne() throws SQLException {
        final var race = this.rows.race();
        final var first = this.rows.slot(race);
        final var second = this.rows.slot(race);
        final var ballot = this.rows.ballot(this.rows.electionId);
        final var gap = this.rows.ballot(this.rows.electionId);
        this.run("update race set method = 'ranked-choice' where id = ?", race);
        this.refused(VOTE, "Ranked choice elects one winner", this.vote(ballot, first, "1"));
        this.run(
            "update seat set appointments = 1 where id = (select seat_id from term where id = ?)",
            this.rows.termId
        );
        this.refused(VOTE, "Ranked-choice ranks are 1, 2, or 3", this.vote(ballot, first, "4"));
        this.refused(VOTE, "Ranked-choice ranks are contiguous from 1", this.vote(gap, first, "2"));
        this.run(VOTE, this.vote(ballot, first, "1"));
        this.refused(VOTE, "Ranked-choice ranks are contiguous from 1", this.vote(ballot, second, "1"));
        this.run(VOTE, this.vote(ballot, second, "2"));
    }

    @Test
    void primaryRacesOnOneBallotShareOneParty() throws SQLException {
        final var mayor = this.rows.race();
        final var council = this.rows.race();
        final var blue = this.rows.slot(mayor);
        final var red = this.rows.slot(council);
        final var ballot = this.rows.ballot(this.rows.electionId);
        final var party = "update candidate set party_affiliation = ?"
            + " where id = (select candidate_id from race_candidate where id = ?)";
        this.run("update race set type = 'primary' where id in (?, ?)", mayor, council);
        this.run(party, "Blue", blue);
        this.run(party, "Red", red);
        this.run(VOTE, this.vote(ballot, blue, "Y"));
        this.refused(VOTE, "Primary races on one ballot share one party", this.vote(ballot, red, "Y"));
    }

    @Test
    void aRaceRoundFollowsItsPreviousRace() throws SQLException {
        final var first = this.rows.race();
        final var method = "first-past-the-post";
        this.refused(RACE, "The first round of a race is 1", this.race(null, method, 2));
        this.refused(RACE, "A race round follows its previous race", this.race(first, method, 3));
        this.refused(RACE, "A race keeps the method of its previous race", this.race(first, "ranked-choice", 2));
        this.run(RACE, this.race(first, method, 2));
        this.refused("update race set round = 2 where id = ?", "The first round of a race is 1", first);
    }

    @Test
    void termsForOneSeatDoNotOverlap() throws SQLException {
        final var seat = UUID.randomUUID();
        final var deleted = UUID.randomUUID();
        this.run(
            "insert into seat (id, tenant_id, locality_id, name, appointments, term_length)"
                + " values (?, ?, ?, 'Mayor', 1, 4)",
            seat,
            this.rows.tenantId,
            this.rows.localityId
        );
        this.run(TERM, this.term(seat, "2025-01-01", "2028-12-31"));
        this.refused(TERM, "term_no_overlap", this.term(seat, "2028-12-31", "2032-12-31"));
        this.refused(TERM, "term_dates_ordered", this.term(seat, "2034-01-01", "2033-12-31"));
        this.run(TERM, this.term(seat, "2029-01-01", "2032-12-31"));
        this.run(TERM, this.term(seat, null, null));
        this.run(TERM, this.termWithId(deleted, seat, "2033-01-01", "2036-12-31"));
        this.run("update term set deleted_at = now() where id = ?", deleted);
        this.run(TERM, this.term(seat, "2033-01-01", "2036-12-31"));
    }

    private Object[] ballotValues(final String hash, final Timestamp timestamp) {
        return new Object[] {
            UUID.randomUUID(),
            this.rows.tenantId,
            this.rows.electionId,
            this.rows.precinctId,
            ParentRows.accessHash(hash),
            timestamp,
        };
    }

    private Object[] vote(final UUID ballot, final UUID slot, final String value) {
        return new Object[] {UUID.randomUUID(), this.rows.tenantId, ballot, slot, value};
    }

    private Object[] race(final UUID previous, final String method, final int round) {
        return new Object[] {
            UUID.randomUUID(),
            this.rows.tenantId,
            this.rows.electionId,
            this.rows.termId,
            previous,
            method,
            round,
        };
    }

    private Object[] term(final UUID seat, final String start, final String end) {
        return this.termWithId(UUID.randomUUID(), seat, start, end);
    }

    private Object[] termWithId(final UUID id, final UUID seat, final String start, final String end) {
        return new Object[] {id, this.rows.tenantId, seat, this.date(start), this.date(end)};
    }

    private LocalDate date(final String text) {
        if (text == null) {
            return null;
        }
        else {
            return LocalDate.parse(text);
        }
    }

    private void refusedName(final UUID slot, final String name, final String message) {
        this.refused(VOTE, message, this.vote(this.rows.ballot(this.rows.electionId), slot, name));
    }

    private void refused(final String sql, final String message, final Object... values) {
        final var failure = assertThrows(SQLException.class, () -> this.run(sql, values));
        final var text = failure.getMessage();
        assertTrue(text.contains(message), text);
    }

    private void run(final String sql, final Object... values) throws SQLException {
        final var connection = this.connect();
        try {
            this.execute(connection, sql, values);
        }
        finally {
            connection.close();
        }
    }

    private void execute(final Connection connection, final String sql, final Object... values) throws SQLException {
        final var statement = connection.prepareStatement(sql);
        try {
            this.bind(statement, values);
            statement.executeUpdate();
        }
        finally {
            statement.close();
        }
    }

    private void bind(final PreparedStatement statement, final Object... values) throws SQLException {
        var index = 0;
        for (final var value : values) {
            index = index + 1;
            statement.setObject(index, value);
        }
    }

    private Connection connect() throws SQLException {
        final var container = postgres;
        return DriverManager.getConnection(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }
}
