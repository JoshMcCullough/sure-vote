package org.sure_vote.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.Identifiable;
import org.sure_vote.entity.Locality;
import org.sure_vote.entity.SystemAdmin;
import org.sure_vote.entity.SystemAdminStatus;
import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.TenantKind;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresIsolationTest {
    private static final String WRITER = "tenant_writer";

    private static final String SECRET = "writer";

    private static PostgreSQLContainer<?> postgres;

    private static JdbcSureVoteRepository owner;

    private static JdbcSureVoteRepository writer;

    private ParentRows first;

    private ParentRows second;

    @BeforeAll
    static void start() throws SQLException {
        final var container = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres = container;
        container.start();
        PostgresIsolationTest.open(container);
    }

    private static void open(final PostgreSQLContainer<?> container) throws SQLException {
        final var url = container.getJdbcUrl();
        final var user = container.getUsername();
        final var password = container.getPassword();
        JdbcSureVoteRepository.migrate(url, user, password);
        owner = JdbcSureVoteRepository.open(url, user, password);
        PostgresIsolationTest.grantWriter(url, user, password);
        writer = JdbcSureVoteRepository.open(url, WRITER, SECRET);
    }

    private static void grantWriter(final String url, final String user, final String password) throws SQLException {
        final var connection = DriverManager.getConnection(url, user, password);
        final var statement = connection.createStatement();
        try {
            statement.execute("create role " + WRITER + " login password '" + SECRET + "'");
            statement.execute("grant usage on schema public to " + WRITER);
            statement.execute("grant select, insert, update on all tables in schema public to " + WRITER);
        }
        finally {
            statement.close();
            connection.close();
        }
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
        owner.clear();
        this.first = new ParentRows(owner);
        this.second = new ParentRows(owner);
    }

    @Test
    void anUnscopedRoleSeesNoTenantRows() {
        final var hidden = writer.find(Election.class, this.first.electionId);
        final var all = owner.list(Election.class);
        assertEquals(List.of(), writer.list(Election.class));
        assertEquals(List.of(), writer.list(Tenant.class));
        assertTrue(hidden.isEmpty());
        assertEquals(2, all.size());
    }

    @Test
    void aScopeSeesOnlyItsTenant() {
        final var elections = writer.inTenant(this.first.tenantId, () -> writer.list(Election.class));
        final var tenants = writer.inTenant(this.first.tenantId, () -> writer.list(Tenant.class));
        final var other = writer.inTenant(
            this.first.tenantId,
            () -> writer.find(Election.class, this.second.electionId)
        );
        assertEquals(List.of(this.first.electionId), PostgresIsolationTest.ids(elections));
        assertEquals(List.of(this.first.tenantId), PostgresIsolationTest.ids(tenants));
        assertTrue(other.isEmpty());
    }

    @Test
    void aScopeWritesOnlyItsTenant() {
        final var own = this.locality(this.first.tenantId);
        final var foreign = this.locality(this.second.tenantId);
        final var saved = writer.inTenant(this.first.tenantId, () -> writer.insert(own));
        final var failure = assertThrows(
            IllegalStateException.class,
            () -> writer.inTenant(this.first.tenantId, () -> writer.insert(foreign))
        );
        final var text = failure.getMessage();
        final var kept = owner.find(Locality.class, saved.getId());
        final var refused = owner.find(Locality.class, foreign.getId());
        assertTrue(text.contains("row-level security"), text);
        assertTrue(kept.isPresent());
        assertTrue(refused.isEmpty());
    }

    @Test
    void aScopeCannotPointAtAnotherTenantsRow() throws SQLException {
        final var failure = assertThrows(SQLException.class, this::insertForeignRace);
        final var text = failure.getMessage();
        assertTrue(text.contains("race tenant_id does not match its election"), text);
    }

    @Test
    void aTransactionServesOneTenant() {
        final var failure = assertThrows(
            IllegalArgumentException.class,
            () -> writer.inTenant(
                this.first.tenantId,
                () -> writer.inTenant(this.second.tenantId, () -> writer.list(Election.class))
            )
        );
        final var again = writer.inTenant(
            this.first.tenantId,
            () -> writer.inTenant(this.first.tenantId, () -> writer.list(Election.class))
        );
        assertEquals("A transaction serves one tenant", failure.getMessage());
        assertEquals(1, again.size());
        assertThrows(IllegalArgumentException.class, () -> writer.inTenant(null, () -> writer.list(Election.class)));
    }

    @Test
    void theScopeEndsWithItsTransaction() {
        final var during = writer.transaction(() -> this.scopedThenUnset());
        assertEquals(List.of(1, 0), during);
    }

    @Test
    void theFirstSystemAdminCanBeInsertedWithoutAScope() {
        final var admin = this.admin(SystemAdminStatus.ACTIVE);
        final var second = this.admin(SystemAdminStatus.ACTIVE);
        final var saved = writer.asSystemAdmin(admin.getId(), () -> writer.insert(admin));
        final var failure = assertThrows(
            IllegalStateException.class,
            () -> writer.asSystemAdmin(second.getId(), () -> writer.insert(second))
        );
        final var text = failure.getMessage();
        final var unscoped = writer.list(SystemAdmin.class);
        assertEquals(admin.getId(), saved.getId());
        assertTrue(text.contains("row-level security"), text);
        assertEquals(List.of(), unscoped);
    }

    @Test
    void aSystemAdminManagesEveryTenantButNoTenantRows() {
        final var admin = owner.insert(this.admin(SystemAdminStatus.ACTIVE));
        final var tenant = writer.asSystemAdmin(admin.getId(), () -> writer.insert(this.tenant()));
        final var stored = owner.find(Tenant.class, tenant.getId());
        final var tenants = writer.asSystemAdmin(admin.getId(), () -> writer.list(Tenant.class));
        final var elections = writer.asSystemAdmin(admin.getId(), () -> writer.list(Election.class));
        final var admins = writer.asSystemAdmin(admin.getId(), () -> writer.list(SystemAdmin.class));
        assertEquals(3, tenants.size());
        assertEquals(List.of(), elections);
        assertEquals(List.of(admin.getId()), PostgresIsolationTest.ids(admins));
        assertTrue(stored.isPresent());
    }

    @Test
    void onlyAnActiveSystemAdminHasTheScope() {
        final var active = owner.insert(this.admin(SystemAdminStatus.ACTIVE));
        final var disabled = owner.insert(this.admin(SystemAdminStatus.DISABLED));
        final var stranger = UUID.randomUUID();
        final var tenant = this.tenant();
        final var hidden = writer.asSystemAdmin(disabled.getId(), () -> writer.list(Tenant.class));
        final var strange = writer.asSystemAdmin(stranger, () -> writer.list(Tenant.class));
        final var failure = assertThrows(
            IllegalStateException.class,
            () -> writer.asSystemAdmin(stranger, () -> writer.insert(tenant))
        );
        final var unscoped = assertThrows(IllegalStateException.class, () -> writer.insert(this.tenant()));
        final var scopedText = failure.getMessage();
        final var unscopedText = unscoped.getMessage();
        final var visible = writer.asSystemAdmin(active.getId(), () -> writer.list(Tenant.class));
        assertEquals(List.of(), hidden);
        assertEquals(List.of(), strange);
        assertEquals(2, visible.size());
        assertTrue(scopedText.contains("row-level security"), scopedText);
        assertTrue(unscopedText.contains("row-level security"), unscopedText);
    }

    @Test
    void aTransactionServesOneSystemAdmin() {
        final var failure = assertThrows(
            IllegalArgumentException.class,
            () -> writer.asSystemAdmin(
                UUID.randomUUID(),
                () -> writer.asSystemAdmin(UUID.randomUUID(), () -> writer.list(Tenant.class))
            )
        );
        assertEquals("A transaction serves one system admin", failure.getMessage());
        assertThrows(IllegalArgumentException.class, () -> writer.asSystemAdmin(null, () -> writer.list(Tenant.class)));
    }

    private SystemAdmin admin(final SystemAdminStatus status) {
        final var admin = new SystemAdmin();
        admin.setId(UUID.randomUUID());
        admin.setName("Host");
        admin.setStatus(status);
        return admin;
    }

    private Tenant tenant() {
        final var tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("County");
        tenant.setKind(TenantKind.PRODUCTION);
        return tenant;
    }

    private List<Integer> scopedThenUnset() {
        final var scoped = writer.inTenant(this.first.tenantId, () -> writer.list(Election.class));
        final var after = writer.list(Election.class);
        return List.of(scoped.size(), after.size());
    }

    private Locality locality(final UUID tenantId) {
        final var locality = new Locality();
        locality.setId(UUID.randomUUID());
        locality.setTenantId(tenantId);
        locality.setName("Ward");
        return locality;
    }

    private void insertForeignRace() throws SQLException {
        final var container = postgres;
        final var connection = DriverManager.getConnection(container.getJdbcUrl(), WRITER, SECRET);
        try {
            connection.setAutoCommit(false);
            this.scope(connection, this.first.tenantId);
            this.insertRace(connection);
            connection.commit();
        }
        finally {
            connection.close();
        }
    }

    private void scope(final Connection connection, final UUID tenantId) throws SQLException {
        final var statement = connection.prepareStatement("select set_config('app.tenant_id', ?, true)");
        try {
            statement.setString(1, tenantId.toString());
            statement.execute();
        }
        finally {
            statement.close();
        }
    }

    private void insertRace(final Connection connection) throws SQLException {
        final var statement = connection.prepareStatement(
            "insert into race (id, tenant_id, election_id, term_id, name, round) values (?, ?, ?, ?, 'Mayor', 1)"
        );
        try {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, this.first.tenantId);
            statement.setObject(3, this.second.electionId);
            statement.setObject(4, this.first.termId);
            statement.executeUpdate();
        }
        finally {
            statement.close();
        }
    }

    private static List<UUID> ids(final List<? extends Identifiable> rows) {
        final var stream = rows.stream();
        final var mapped = stream.map(Identifiable::getId);
        return mapped.toList();
    }
}
