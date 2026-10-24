package org.sure_vote.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;
import org.sure_vote.entity.Address;
import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.BallotDispute;
import org.sure_vote.entity.BallotIssuance;
import org.sure_vote.entity.BallotStyle;
import org.sure_vote.entity.Candidate;
import org.sure_vote.entity.CanvassStatement;
import org.sure_vote.entity.ContentFile;
import org.sure_vote.entity.ElectedOfficial;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.HeldKey;
import org.sure_vote.entity.ElectionVotingPrecinctAssignment;
import org.sure_vote.entity.Identifiable;
import org.sure_vote.entity.Locality;
import org.sure_vote.entity.Person;
import org.sure_vote.entity.PrecinctDistrict;
import org.sure_vote.entity.Race;
import org.sure_vote.entity.RaceCandidate;
import org.sure_vote.entity.RecordingDevice;
import org.sure_vote.entity.RecordingPlace;
import org.sure_vote.entity.RestoreIncident;
import org.sure_vote.entity.Seat;
import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.TenantValidator;
import org.sure_vote.entity.Term;
import org.sure_vote.entity.Vote;
import org.sure_vote.entity.VoteChain;
import org.sure_vote.entity.VoteChainEntry;
import org.sure_vote.entity.VoteChainValidationResult;
import org.sure_vote.entity.VoteChainValidator;
import org.sure_vote.entity.VoterRegistration;
import org.sure_vote.entity.VotingDistrict;
import org.sure_vote.entity.VotingPrecinct;

/**
 * PostgreSQL store. One transaction holds one connection. Nested work sets a savepoint and does not commit.
 * An outer failure rolls the nested writes back with the outer work.
 */
public final class JdbcSureVoteRepository implements SureVoteRepository {
    private static final List<Class<?>> TABLES = List.of(
        Address.class,
        Ballot.class,
        BallotDispute.class,
        BallotIssuance.class,
        BallotStyle.class,
        Candidate.class,
        CanvassStatement.class,
        ContentFile.class,
        ElectedOfficial.class,
        Election.class,
        ElectionVotingPrecinctAssignment.class,
        HeldKey.class,
        Locality.class,
        Person.class,
        PrecinctDistrict.class,
        Race.class,
        RaceCandidate.class,
        RecordingDevice.class,
        RecordingPlace.class,
        RestoreIncident.class,
        Seat.class,
        Tenant.class,
        TenantValidator.class,
        Term.class,
        Vote.class,
        VoteChain.class,
        VoteChainEntry.class,
        VoteChainValidationResult.class,
        VoteChainValidator.class,
        VoterRegistration.class,
        VotingDistrict.class,
        VotingPrecinct.class
    );

    private final DataSource source;
    private final ThreadLocal<Work> work = new ThreadLocal<>();

    private JdbcSureVoteRepository(final DataSource source) {
        this.source = source;
    }

    public static void migrate(final String url, final String user, final String password) {
        final var configured = Flyway.configure();
        final var sourced = configured.dataSource(url, user, password);
        final var flyway = sourced.load();
        flyway.migrate();
    }

    public static JdbcSureVoteRepository open(final String url, final String user, final String password) {
        final var source = new PGSimpleDataSource();
        source.setURL(url);
        source.setUser(user);
        source.setPassword(password);
        return new JdbcSureVoteRepository(source);
    }

    @Override
    public <T extends Identifiable> T insert(final T row) {
        final var joined = this.joined();
        if (joined == null) {
            return this.alone(connection -> this.insertJoined(connection, row));
        }
        else {
            return this.insertJoined(joined, row);
        }
    }

    @Override
    public <T extends Identifiable> T replace(final T row) {
        final var joined = this.joined();
        if (joined == null) {
            return this.alone(connection -> this.replaceJoined(connection, row));
        }
        else {
            return this.replaceJoined(joined, row);
        }
    }

    @Override
    public <T extends Identifiable> Optional<T> find(final Class<T> type, final UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        else {
            return this.findPresent(type, id);
        }
    }

    @Override
    public <T extends Identifiable> List<T> list(final Class<T> type) {
        final var joined = this.joined();
        if (joined == null) {
            return this.alone(connection -> this.listJoined(connection, type));
        }
        else {
            return this.listJoined(joined, type);
        }
    }

    @Override
    public <T> T transaction(final Supplier<T> body) {
        final var joined = this.work.get();
        if (joined == null) {
            return this.outer(body);
        }
        else {
            return this.nested(joined, body);
        }
    }

    void clear() {
        final var names = RowBinding.tables(TABLES);
        final var joined = String.join(", ", names);
        final var sql = "truncate table " + joined + " cascade";
        this.alone(connection -> this.execute(connection, sql));
    }

    private <T extends Identifiable> Optional<T> findPresent(final Class<T> type, final UUID id) {
        final var joined = this.joined();
        if (joined == null) {
            return this.alone(connection -> this.findJoined(connection, type, id));
        }
        else {
            return this.findJoined(joined, type, id);
        }
    }

    private Connection joined() {
        final var session = this.work.get();
        if (session == null) {
            return null;
        }
        else {
            return session.connection;
        }
    }

    private <T> T outer(final Supplier<T> body) {
        final var connection = this.open();
        final var session = new Work(connection);
        this.work.set(session);
        try {
            return this.commitSupplier(connection, body);
        }
        finally {
            this.work.remove();
            this.close(connection);
        }
    }

    private <T> T nested(final Work session, final Supplier<T> body) {
        final var name = "sv" + session.depth;
        final var savepoint = this.mark(session.connection, name);
        session.depth = session.depth + 1;
        try {
            return body.get();
        }
        catch (final RuntimeException failure) {
            this.rollback(session.connection, savepoint, failure);
            throw failure;
        }
        finally {
            session.depth = session.depth - 1;
        }
    }

    private <T> T alone(final SqlWork<T> body) {
        final var connection = this.open();
        try {
            return this.commitWork(connection, body);
        }
        finally {
            this.close(connection);
        }
    }

    private <T> T commitSupplier(final Connection connection, final Supplier<T> body) {
        try {
            final var result = body.get();
            connection.commit();
            return result;
        }
        catch (final RuntimeException failure) {
            this.rollback(connection, failure);
            throw failure;
        }
        catch (final SQLException failure) {
            this.rollback(connection, failure);
            throw new IllegalStateException(failure.getMessage(), failure);
        }
    }

    private <T> T commitWork(final Connection connection, final SqlWork<T> body) {
        try {
            final var result = body.run(connection);
            connection.commit();
            return result;
        }
        catch (final RuntimeException failure) {
            this.rollback(connection, failure);
            throw failure;
        }
        catch (final SQLException failure) {
            this.rollback(connection, failure);
            throw new IllegalStateException(failure.getMessage(), failure);
        }
    }

    private <T extends Identifiable> T insertJoined(final Connection connection, final T row) {
        RowRules.insert(connection, row);
        this.executeInsert(connection, row);
        return row;
    }

    private <T extends Identifiable> T replaceJoined(final Connection connection, final T row) {
        RowRules.replace(connection, row);
        this.executeUpdate(connection, row);
        return row;
    }

    private void executeInsert(final Connection connection, final Identifiable row) {
        try {
            this.insertStatement(connection, row);
        }
        catch (final SQLException failure) {
            throw new IllegalStateException(failure.getMessage(), failure);
        }
    }

    private void insertStatement(final Connection connection, final Identifiable row) throws SQLException {
        final var sql = RowBinding.insertSql(row.getClass());
        final var statement = connection.prepareStatement(sql);
        try {
            RowBinding.bindInsert(statement, row);
            statement.executeUpdate();
        }
        finally {
            statement.close();
        }
    }

    private void executeUpdate(final Connection connection, final Identifiable row) {
        try {
            this.updateStatement(connection, row);
        }
        catch (final SQLException failure) {
            throw new IllegalStateException(failure.getMessage(), failure);
        }
    }

    private void updateStatement(final Connection connection, final Identifiable row) throws SQLException {
        final var sql = RowBinding.updateSql(row.getClass());
        final var statement = connection.prepareStatement(sql);
        try {
            RowBinding.bindUpdate(statement, row);
            statement.executeUpdate();
        }
        finally {
            statement.close();
        }
    }

    private <T extends Identifiable> Optional<T> findJoined(
        final Connection connection,
        final Class<T> type,
        final UUID id
    ) {
        try {
            return this.queryOne(connection, type, id);
        }
        catch (final SQLException failure) {
            throw new IllegalStateException(failure.getMessage(), failure);
        }
    }

    private <T extends Identifiable> Optional<T> queryOne(
        final Connection connection,
        final Class<T> type,
        final UUID id
    ) throws SQLException {
        final var sql = RowBinding.findSql(type);
        final var statement = connection.prepareStatement(sql);
        try {
            statement.setObject(1, id);
            return this.readOne(statement, type);
        }
        finally {
            statement.close();
        }
    }

    private <T extends Identifiable> Optional<T> readOne(
        final java.sql.PreparedStatement statement,
        final Class<T> type
    ) throws SQLException {
        final var result = statement.executeQuery();
        try {
            return this.one(result, type);
        }
        finally {
            result.close();
        }
    }

    private <T extends Identifiable> Optional<T> one(final java.sql.ResultSet result, final Class<T> type)
        throws SQLException {
        if (result.next()) {
            return Optional.of(RowBinding.read(result, type));
        }
        else {
            return Optional.empty();
        }
    }

    private <T extends Identifiable> List<T> listJoined(final Connection connection, final Class<T> type) {
        try {
            return this.queryList(connection, type);
        }
        catch (final SQLException failure) {
            throw new IllegalStateException(failure.getMessage(), failure);
        }
    }

    private <T extends Identifiable> List<T> queryList(final Connection connection, final Class<T> type)
        throws SQLException {
        final var sql = RowBinding.listSql(type);
        final var statement = connection.prepareStatement(sql);
        try {
            return this.readList(statement, type);
        }
        finally {
            statement.close();
        }
    }

    private <T extends Identifiable> List<T> readList(
        final java.sql.PreparedStatement statement,
        final Class<T> type
    ) throws SQLException {
        final var result = statement.executeQuery();
        try {
            return this.collect(result, type);
        }
        finally {
            result.close();
        }
    }

    private <T extends Identifiable> List<T> collect(final java.sql.ResultSet result, final Class<T> type)
        throws SQLException {
        final var rows = new ArrayList<T>();
        while (result.next()) {
            rows.add(RowBinding.read(result, type));
        }
        return List.copyOf(rows);
    }

    private Integer execute(final Connection connection, final String sql) throws SQLException {
        final var statement = connection.createStatement();
        try {
            statement.execute(sql);
            return Integer.valueOf(0);
        }
        finally {
            statement.close();
        }
    }

    private Connection open() {
        try {
            return this.connected();
        }
        catch (final SQLException failure) {
            throw new IllegalStateException(failure.getMessage(), failure);
        }
    }

    private Connection connected() throws SQLException {
        final var connection = this.source.getConnection();
        connection.setAutoCommit(false);
        return connection;
    }

    private Savepoint mark(final Connection connection, final String name) {
        try {
            return connection.setSavepoint(name);
        }
        catch (final SQLException failure) {
            throw new IllegalStateException(failure.getMessage(), failure);
        }
    }

    private void rollback(final Connection connection, final Exception failure) {
        try {
            connection.rollback();
        }
        catch (final SQLException ignored) {
            failure.addSuppressed(ignored);
        }
    }

    private void rollback(final Connection connection, final Savepoint savepoint, final RuntimeException failure) {
        try {
            connection.rollback(savepoint);
        }
        catch (final SQLException ignored) {
            failure.addSuppressed(ignored);
        }
    }

    private void close(final Connection connection) {
        try {
            connection.close();
        }
        catch (final SQLException failure) {
            throw new IllegalStateException(failure.getMessage(), failure);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(final Connection connection) throws SQLException;
    }

    private static final class Work {
        private final Connection connection;
        private int depth;

        private Work(final Connection connection) {
            this.connection = connection;
        }
    }
}
