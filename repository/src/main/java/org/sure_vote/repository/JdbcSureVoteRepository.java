package org.sure_vote.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;
import org.sure_vote.entity.Address;
import org.sure_vote.entity.AuthCeremony;
import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.BallotDispute;
import org.sure_vote.entity.BallotDispute;
import org.sure_vote.entity.BallotIssuance;
import org.sure_vote.entity.BallotStyle;
import org.sure_vote.entity.Candidate;
import org.sure_vote.entity.CanvassStatement;
import org.sure_vote.entity.ContentFile;
import org.sure_vote.entity.Credential;
import org.sure_vote.entity.ElectedOfficial;
import org.sure_vote.entity.CloseBundle;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.ElectionVotingPrecinctAssignment;
import org.sure_vote.entity.Enrollment;
import org.sure_vote.entity.HeldKey;
import org.sure_vote.entity.Identifiable;
import org.sure_vote.entity.Locality;
import org.sure_vote.entity.Person;
import org.sure_vote.entity.PrecinctDistrict;
import org.sure_vote.entity.Race;
import org.sure_vote.entity.RaceCandidate;
import org.sure_vote.entity.RecordingDevice;
import org.sure_vote.entity.RecordingPlace;
import org.sure_vote.entity.RequestNonce;
import org.sure_vote.entity.RestoreIncident;
import org.sure_vote.entity.Seat;
import org.sure_vote.entity.Session;
import org.sure_vote.entity.SystemAdmin;
import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.TenantOfficial;
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
        AuthCeremony.class,
        Ballot.class,
        BallotDispute.class,
        BallotIssuance.class,
        BallotStyle.class,
        Candidate.class,
        CanvassStatement.class,
        ContentFile.class,
        Credential.class,
        ElectedOfficial.class,
        CloseBundle.class,
        Election.class,
        Enrollment.class,
        ElectionVotingPrecinctAssignment.class,
        HeldKey.class,
        Locality.class,
        Person.class,
        PrecinctDistrict.class,
        Race.class,
        RaceCandidate.class,
        RecordingDevice.class,
        RecordingPlace.class,
        RequestNonce.class,
        RestoreIncident.class,
        Seat.class,
        Session.class,
        SystemAdmin.class,
        Tenant.class,
        TenantOfficial.class,
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

    private static final String TENANT = "app.tenant_id";

    private static final String SYSTEM_ADMIN = "app.system_admin_id";

    private static final Map<String, String> LABELS = Map.of(TENANT, "tenant", SYSTEM_ADMIN, "system admin");

    private static final List<String> PUBLIC_TABLES = List.of(
        "tenant",
        "election",
        "race",
        "race_candidate",
        "candidate",
        "vote_chain",
        "vote_chain_entry",
        "vote",
        "vote_chain_validator",
        "tenant_validator",
        "vote_chain_validation_result",
        "restore_incident",
        "canvass_statement",
        "close_bundle"
    );

    private final DataSource source;
    private final ThreadLocal<Work> work = new ThreadLocal<>();
    private final TenantLock tenantLock = new TenantLock();

    private JdbcSureVoteRepository(final DataSource source) {
        this.source = source;
    }

    /**
     * The highest migration this build ships. {@code SchemaVersionTest} keeps it equal to the newest file in
     * {@code db/migration}. Surfaces that do not migrate wait until the database has reached it.
     */
    public static final String SCHEMA_VERSION = "18";

    /**
     * The highest migration applied to the database, or an empty string before the first. Any role the workspace
     * granted can read it, because each grant includes select on {@code flyway_schema_history}.
     */
    public static String schemaVersion(final String url, final String user, final String password) {
        final var repository = open(url, user, password);
        return repository.alone(repository::appliedVersion);
    }

    private String appliedVersion(final Connection connection) throws SQLException {
        final var sql = "select version from flyway_schema_history where success and version is not null";
        final var statement = connection.createStatement();
        try {
            return this.highest(statement.executeQuery(sql));
        }
        finally {
            statement.close();
        }
    }

    private String highest(final ResultSet rows) throws SQLException {
        var highest = "";
        while (rows.next()) {
            final var version = rows.getString(1);
            if (SchemaVersions.compare(version, highest) > 0) {
                highest = version;
            }
        }
        return highest;
    }

    public static void migrate(final String url, final String user, final String password) {
        final var configured = Flyway.configure();
        final var sourced = configured.dataSource(url, user, password);
        final var flyway = sourced.load();
        flyway.migrate();
    }

    public static void grantWriter(final String url, final String user, final String password, final String role) {
        if (role == null || !role.matches("[a-z_][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("writer role must be a lowercase postgres identifier");
        }
        else {
            final var owner = open(url, user, password);
            owner.alone(connection -> owner.granted(connection, role));
        }
    }

    private Integer granted(final Connection connection, final String role) throws SQLException {
        final var sql = "do $$ begin "
            + "execute format('grant usage on schema %I to " + role + "', current_schema()); "
            + "execute format('grant select, insert, update on all tables in schema %I to " + role + "', "
            + "current_schema()); "
            + "execute 'revoke insert, update on flyway_schema_history from " + role + "'; end $$";
        return this.execute(connection, sql);
    }

    public static void grantReader(final String url, final String user, final String password, final String role) {
        if (role == null || !role.matches("[a-z_][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("reader role must be a lowercase postgres identifier");
        }
        else {
            final var owner = open(url, user, password);
            owner.alone(connection -> owner.readable(connection, role));
        }
    }

    private Integer readable(final Connection connection, final String role) throws SQLException {
        final var tables = String.join(", ", PUBLIC_TABLES);
        final var sql = "do $$ begin "
            + "execute format('grant usage on schema %I to " + role + "', current_schema()); "
            + "execute format('revoke all on all tables in schema %I from " + role + "', current_schema()); "
            + "execute 'grant select on " + tables + " to " + role + "'; "
            + "execute 'grant select on flyway_schema_history to " + role + "'; end $$";
        return this.execute(connection, sql);
    }

    /**
     * The public surface's dispute role: the reader's published tables, insert on {@code ballot_dispute}, and select on
     * its {@code id} column only, for the duplicate-id check. It reads disputes back through {@code ballot_disputes}.
     */
    public static void grantDispute(final String url, final String user, final String password, final String role) {
        if (role == null || !role.matches("[a-z_][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("dispute role must be a lowercase postgres identifier");
        }
        else {
            final var owner = open(url, user, password);
            owner.alone(connection -> owner.disputable(connection, role));
        }
    }

    private Integer disputable(final Connection connection, final String role) throws SQLException {
        final var sql = "grant insert, select (\"id\") on ballot_dispute to " + role;
        this.readable(connection, role);
        return this.execute(connection, sql);
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

    @Override
    public int pruneNonces(final Instant before) {
        return this.query(connection -> this.pruned(connection, before));
    }

    private int pruned(final Connection connection, final Instant before) throws SQLException {
        final var statement = connection.prepareStatement("select prune_request_nonces(?)");
        try {
            statement.setObject(1, OffsetDateTime.ofInstant(before, ZoneOffset.UTC));
            return this.firstInt(statement);
        }
        finally {
            statement.close();
        }
    }

    @Override
    public void lock(final UUID key) {
        this.query(connection -> this.advisory(connection, key));
    }

    private Integer advisory(final Connection connection, final UUID key) throws SQLException {
        final var value = key.getMostSignificantBits() ^ key.getLeastSignificantBits();
        final var statement = connection.prepareStatement("select pg_advisory_xact_lock(?)");
        try {
            statement.setLong(1, value);
            statement.execute();
            return 0;
        }
        finally {
            statement.close();
        }
    }

    @Override
    public void afterCommit(final Runnable action) {
        final var session = this.work.get();
        if (session == null) {
            action.run();
        }
        else {
            session.after.add(action);
        }
    }

    @Override
    public List<Ballot> ballotsByAccessHash(final String voterAccessHash) {
        return this.query(connection -> this.lookupBallots(connection, voterAccessHash));
    }

    @Override
    public List<BallotDispute> disputesByAccessHash(final String voterAccessHash) {
        return this.query(connection -> this.disputesOf(connection, voterAccessHash));
    }

    @Override
    public int disputeCount(final UUID electionId) {
        return this.query(connection -> this.disputesIn(connection, electionId));
    }

    @Override
    public Optional<UUID> electionTenant(final UUID electionId) {
        return this.query(connection -> this.tenantOf(connection, electionId));
    }

    @Override
    public Map<UUID, String> candidateNames(final UUID electionId) {
        return this.query(connection -> this.namesOf(connection, electionId));
    }

    @Override
    public int markReceived(final List<UUID> issuanceIds, final Instant receivedAt) {
        return this.query(connection -> this.marked(connection, issuanceIds, receivedAt));
    }

    @Override
    public <T> T inTenant(final UUID tenantId, final Supplier<T> body) {
        this.tenantLock.check(tenantId);
        return this.scope(TENANT, tenantId, body);
    }

    @Override
    public void lockTenants(final Set<UUID> tenantIds) {
        this.tenantLock.lock(tenantIds);
    }

    @Override
    public <T> T asSystemAdmin(final UUID adminId, final Supplier<T> body) {
        return this.scope(SYSTEM_ADMIN, adminId, body);
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

    private <T> T scope(final String setting, final UUID value, final Supplier<T> body) {
        if (value == null) {
            throw new IllegalArgumentException(LABELS.get(setting) + " is required");
        }
        else {
            return this.transaction(() -> this.scoped(setting, value, body));
        }
    }

    private <T> T scoped(final String setting, final UUID value, final Supplier<T> body) {
        final var session = this.work.get();
        final var current = session.scopes.get(setting);
        if (current != null && !current.equals(value)) {
            throw new IllegalArgumentException("A transaction serves one " + LABELS.get(setting));
        }
        else {
            return this.within(session, setting, value, body);
        }
    }

    private <T> T within(final Work session, final String setting, final UUID value, final Supplier<T> body) {
        final var previous = session.scopes.get(setting);
        this.setScope(session, setting, value.toString());
        try {
            final var result = body.get();
            this.restoreScope(session, setting, previous);
            return result;
        }
        catch (final RuntimeException failure) {
            this.forget(session, setting, previous);
            throw failure;
        }
    }

    private void restoreScope(final Work session, final String setting, final UUID previous) {
        if (previous == null) {
            this.setScope(session, setting, "");
        }
    }

    private void forget(final Work session, final String setting, final UUID previous) {
        if (previous == null) {
            session.scopes.remove(setting);
        }
    }

    private void setScope(final Work session, final String setting, final String value) {
        try {
            this.selectSetting(session.connection, setting, value);
            this.remember(session, setting, value);
        }
        catch (final SQLException failure) {
            throw new IllegalStateException(failure.getMessage(), failure);
        }
    }

    private void remember(final Work session, final String setting, final String value) {
        if (value.isEmpty()) {
            session.scopes.remove(setting);
        }
        else {
            session.scopes.put(setting, UUID.fromString(value));
        }
    }

    private void selectSetting(final Connection connection, final String setting, final String value)
        throws SQLException {
        final var statement = connection.prepareStatement("select set_config(?, ?, true)");
        try {
            statement.setString(1, setting);
            statement.setString(2, value);
            statement.execute();
        }
        finally {
            statement.close();
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
        final var result = this.committed(connection, session, body);
        session.after.forEach(Runnable::run);
        return result;
    }

    private <T> T committed(final Connection connection, final Work session, final Supplier<T> body) {
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
        final var pending = session.after.size();
        session.depth = session.depth + 1;
        try {
            return body.get();
        }
        catch (final RuntimeException failure) {
            final var added = session.after.subList(pending, session.after.size());
            this.rollback(session.connection, savepoint, failure);
            added.clear();
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

    private <T> T query(final SqlWork<T> body) {
        final var joined = this.joined();
        if (joined == null) {
            return this.alone(body);
        }
        else {
            return this.joinedQuery(joined, body);
        }
    }

    private <T> T joinedQuery(final Connection connection, final SqlWork<T> body) {
        try {
            return body.run(connection);
        }
        catch (final SQLException failure) {
            throw new IllegalStateException(failure.getMessage(), failure);
        }
    }

    private List<Ballot> lookupBallots(final Connection connection, final String voterAccessHash)
        throws SQLException {
        final var statement = connection.prepareStatement("select * from lookup_ballots(?)");
        try {
            statement.setString(1, voterAccessHash);
            return this.readList(statement, Ballot.class);
        }
        finally {
            statement.close();
        }
    }

    private List<BallotDispute> disputesOf(final Connection connection, final String voterAccessHash)
        throws SQLException {
        final var statement = connection.prepareStatement("select * from ballot_disputes(?)");
        try {
            statement.setString(1, voterAccessHash);
            return this.readList(statement, BallotDispute.class);
        }
        finally {
            statement.close();
        }
    }

    private int disputesIn(final Connection connection, final UUID electionId) throws SQLException {
        final var statement = connection.prepareStatement("select dispute_count(?)");
        try {
            statement.setObject(1, electionId);
            return this.firstInt(statement);
        }
        finally {
            statement.close();
        }
    }

    private int marked(final Connection connection, final List<UUID> ids, final Instant receivedAt)
        throws SQLException {
        final var statement = connection.prepareStatement("select mark_issuances_received(?, ?)");
        final var array = connection.createArrayOf("uuid", ids.toArray());
        try {
            statement.setArray(1, array);
            statement.setObject(2, OffsetDateTime.ofInstant(receivedAt, ZoneOffset.UTC));
            return this.firstInt(statement);
        }
        finally {
            statement.close();
        }
    }

    private int firstInt(final PreparedStatement statement) throws SQLException {
        final var result = statement.executeQuery();
        try {
            result.next();
            return result.getInt(1);
        }
        finally {
            result.close();
        }
    }

    private Map<UUID, String> namesOf(final Connection connection, final UUID electionId) throws SQLException {
        final var statement = connection.prepareStatement("select * from candidate_names(?)");
        try {
            statement.setObject(1, electionId);
            return this.names(statement);
        }
        finally {
            statement.close();
        }
    }

    private Map<UUID, String> names(final java.sql.PreparedStatement statement) throws SQLException {
        final var names = new LinkedHashMap<UUID, String>();
        final var result = statement.executeQuery();
        try {
            while (result.next()) {
                names.put(result.getObject(1, UUID.class), result.getString(2));
            }
            return names;
        }
        finally {
            result.close();
        }
    }

    private Optional<UUID> tenantOf(final Connection connection, final UUID electionId) throws SQLException {
        final var statement = connection.prepareStatement("select election_tenant(?)");
        try {
            statement.setObject(1, electionId);
            return this.firstUuid(statement);
        }
        finally {
            statement.close();
        }
    }

    private Optional<UUID> firstUuid(final java.sql.PreparedStatement statement) throws SQLException {
        final var result = statement.executeQuery();
        try {
            result.next();
            return Optional.ofNullable(result.getObject(1, UUID.class));
        }
        finally {
            result.close();
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
        private final Map<String, UUID> scopes = new HashMap<>();
        private final List<Runnable> after = new ArrayList<>();
        private int depth;

        private Work(final Connection connection) {
            this.connection = connection;
        }
    }
}
