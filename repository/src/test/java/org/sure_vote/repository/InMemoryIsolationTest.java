package org.sure_vote.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.Identifiable;
import org.sure_vote.entity.Locality;
import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.TenantKind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryIsolationTest {
    private InMemorySureVoteRepository repository;

    private ParentRows first;

    private ParentRows second;

    @BeforeEach
    void setUp() {
        this.repository = new InMemorySureVoteRepository();
        this.first = new ParentRows(this.repository);
        this.second = new ParentRows(this.repository);
    }

    @Test
    void outsideAScopeTheStoreActsAsTheOwner() {
        final var elections = this.repository.list(Election.class);
        assertEquals(2, elections.size());
    }

    @Test
    void aScopeSeesOnlyItsTenant() {
        final var tenantId = this.first.tenantId;
        final var elections = this.repository.inTenant(tenantId, () -> this.repository.list(Election.class));
        final var tenants = this.repository.inTenant(tenantId, () -> this.repository.list(Tenant.class));
        final var other = this.repository.inTenant(tenantId, () -> this.foreignElection());
        final var owner = this.repository.inTenant(tenantId, this::secondTenant);
        assertEquals(List.of(this.first.electionId), ids(elections));
        assertEquals(List.of(tenantId), ids(tenants));
        assertTrue(other.isEmpty());
        assertEquals(this.second.tenantId, owner.orElseThrow());
    }

    @Test
    void aScopeWritesOnlyItsTenant() {
        final var own = this.locality(this.first.tenantId);
        final var foreign = this.locality(this.second.tenantId);
        final var saved = this.repository.inTenant(this.first.tenantId, () -> this.repository.insert(own));
        final var failure = assertThrows(IllegalArgumentException.class, () -> this.insertAsFirst(foreign));
        final var moved = assertThrows(IllegalArgumentException.class, () -> this.moveToSecond(saved));
        final var refused = this.repository.find(Locality.class, foreign.getId());
        assertEquals("Row-level security refuses this Locality", failure.getMessage());
        assertEquals("Row-level security refuses this Locality", moved.getMessage());
        assertTrue(refused.isEmpty());
    }

    @Test
    void aScopeCannotReplaceAnotherTenantsRow() {
        final var foreign = this.repository.insert(this.locality(this.second.tenantId));
        final var claimed = this.locality(this.first.tenantId);
        final var failure = assertThrows(IllegalArgumentException.class, () -> this.claim(claimed, foreign));
        assertEquals("Row-level security refuses this Locality", failure.getMessage());
    }

    @Test
    void aTransactionServesOneTenantAndOneAdmin() {
        final var tenantId = this.first.tenantId;
        final var adminId = UUID.randomUUID();
        final var tenant = assertThrows(IllegalArgumentException.class, () -> this.nestedTenants());
        final var admin = assertThrows(IllegalArgumentException.class, () -> this.nestedAdmins(adminId));
        final var again = this.repository.inTenant(tenantId, () -> this.repository.inTenant(tenantId, this::elections));
        final var both = this.repository.inTenant(
            tenantId,
            () -> this.repository.asSystemAdmin(adminId, this::tenants)
        );
        assertEquals("A transaction serves one tenant", tenant.getMessage());
        assertEquals("A transaction serves one system admin", admin.getMessage());
        assertEquals(1, again.size());
        assertEquals(2, both.size());
        assertThrows(IllegalArgumentException.class, () -> this.repository.inTenant(null, this::elections));
        assertThrows(IllegalArgumentException.class, () -> this.repository.asSystemAdmin(null, this::elections));
    }

    @Test
    void aSystemAdminManagesEveryTenantButNoTenantRows() {
        final var adminId = UUID.randomUUID();
        final var tenant = this.repository.asSystemAdmin(adminId, () -> this.repository.insert(this.tenant()));
        final var tenants = this.repository.asSystemAdmin(adminId, this::tenants);
        final var elections = this.repository.asSystemAdmin(adminId, this::elections);
        final var after = this.repository.list(Tenant.class);
        final var afterIds = ids(after);
        assertEquals(3, tenants.size());
        assertEquals(List.of(), elections);
        assertEquals(3, after.size());
        assertTrue(afterIds.contains(tenant.getId()));
    }

    private List<Election> elections() {
        return this.repository.list(Election.class);
    }

    private List<Tenant> tenants() {
        return this.repository.list(Tenant.class);
    }

    private Optional<UUID> secondTenant() {
        return this.repository.electionTenant(this.second.electionId);
    }

    private Optional<Election> foreignElection() {
        return this.repository.find(Election.class, this.second.electionId);
    }

    private void insertAsFirst(final Locality row) {
        this.repository.inTenant(this.first.tenantId, () -> this.repository.insert(row));
    }

    private void replaceAsFirst(final Locality row) {
        this.repository.inTenant(this.first.tenantId, () -> this.repository.replace(row));
    }

    private void claim(final Locality claimed, final Locality foreign) {
        claimed.setId(foreign.getId());
        this.replaceAsFirst(claimed);
    }

    private void moveToSecond(final Locality saved) {
        final var moved = this.locality(this.second.tenantId);
        moved.setId(saved.getId());
        this.replaceAsFirst(moved);
    }

    private void nestedTenants() {
        this.repository.inTenant(
            this.first.tenantId,
            () -> this.repository.inTenant(this.second.tenantId, this::elections)
        );
    }

    private void nestedAdmins(final UUID adminId) {
        this.repository.asSystemAdmin(
            adminId,
            () -> this.repository.asSystemAdmin(UUID.randomUUID(), this::elections)
        );
    }

    private Tenant tenant() {
        final var tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("County");
        tenant.setKind(TenantKind.PRODUCTION);
        return tenant;
    }

    private Locality locality(final UUID tenantId) {
        final var locality = new Locality();
        locality.setId(UUID.randomUUID());
        locality.setTenantId(tenantId);
        locality.setName("Ward");
        return locality;
    }

    private static List<UUID> ids(final List<? extends Identifiable> rows) {
        final var stream = rows.stream();
        return stream
            .map(Identifiable::getId)
            .toList();
    }
}
