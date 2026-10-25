package org.sure_vote.service;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.sure_vote.entity.SystemAdmin;
import org.sure_vote.entity.SystemAdminStatus;
import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.TenantKind;
import org.sure_vote.entity.TenantStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemAdminRulesTest {
    @Test
    void theFirstAdminIsBootstrappedOnce() {
        final var world = SampleElection.showcase();
        final var again = world.app.bootstrapSystemAdmin(this.admin(world.systemAdmin, "Host"));
        final var other = this.admin(UUID.randomUUID(), "Other");
        final var nameless = this.admin(UUID.randomUUID(), null);
        assertEquals(world.systemAdmin, again.getId());
        assertEquals(SystemAdminStatus.ACTIVE, again.getStatus());
        this.refused("a system admin already exists", () -> world.app.bootstrapSystemAdmin(other));
        this.refused("system admin id is required", () -> world.app.bootstrapSystemAdmin(new SystemAdmin()));
        this.refused("name is required", () -> new SureVote(SampleStore.open()).bootstrapSystemAdmin(nameless));
    }

    @Test
    void anAdminCreatesListsAndSuspendsTenants() {
        final var world = SampleElection.showcase();
        final var admin = world.admin();
        final var created = world.app.createSystemTenant(this.tenant(TenantKind.PRODUCTION, null), admin);
        final var listed = world.app.systemTenants(admin);
        final var renamed = world.app.updateSystemTenant(created.getId(), " County ", null, admin);
        final var suspended = world.app.updateSystemTenant(created.getId(), null, TenantStatus.SUSPENDED, admin);
        final var ids = listed
            .stream()
            .map(Tenant::getId)
            .toList();
        assertEquals(TenantStatus.ACTIVE, created.getStatus());
        assertTrue(ids.contains(created.getId()));
        assertTrue(ids.contains(world.tenantId));
        assertEquals("County", renamed.getName());
        assertEquals(TenantStatus.ACTIVE, renamed.getStatus());
        assertEquals("County", suspended.getName());
        assertEquals(TenantStatus.SUSPENDED, suspended.getStatus());
        assertEquals(TenantKind.PRODUCTION, suspended.getKind());
        assertEquals(2, suspended.getVersion());
        this.refused("name is required", () -> world.app.updateSystemTenant(created.getId(), " ", null, admin));
        this.refused("tenant kind is required", () -> world.app.createSystemTenant(this.tenant(null, null), admin));
        this.refused("Missing Tenant", () -> world.app.updateSystemTenant(UUID.randomUUID(), "x", null, admin));
    }

    @Test
    void onlyAnActiveAdminManagesTenants() {
        final var world = SampleElection.showcase();
        final var admin = world.admin();
        final var stranger = UUID.randomUUID();
        final var second = world.app.createSystemAdmin(this.admin(null, "Second"), admin);
        final var admins = world.app.systemAdmins(admin);
        final var disabled = world.app.setSystemAdminStatus(second.getId(), SystemAdminStatus.DISABLED, admin);
        final var tenant = this.tenant(TenantKind.PRODUCTION, TenantStatus.ACTIVE);
        assertEquals(2, admins.size());
        assertEquals(SystemAdminStatus.DISABLED, disabled.getStatus());
        this.refused("not a system admin", () -> world.app.createSystemTenant(tenant, stranger));
        this.refused("not a system admin", () -> world.app.systemTenants(stranger));
        this.refused("not a system admin", () -> world.app.createSystemAdmin(this.admin(null, "X"), stranger));
        this.refused("not a system admin", () -> world.app.systemTenants(second.getId()));
        this.refused("actor is required", () -> world.app.systemTenants(null));
        this.refused(
            "the last active system admin cannot be disabled",
            () -> world.app.setSystemAdminStatus(admin, SystemAdminStatus.DISABLED, admin)
        );
        this.refused(
            "system admin status is required",
            () -> world.app.setSystemAdminStatus(second.getId(), null, admin)
        );
    }

    @Test
    void anotherActiveAdminLetsOneBeDisabled() {
        final var world = SampleElection.showcase();
        final var admin = world.admin();
        final var second = world.app.createSystemAdmin(this.admin(null, "Second"), admin);
        final var disabled = world.app.setSystemAdminStatus(admin, SystemAdminStatus.DISABLED, second.getId());
        assertEquals(SystemAdminStatus.DISABLED, disabled.getStatus());
        this.refused("not a system admin", () -> world.app.systemTenants(admin));
    }

    @Test
    void theDemoTenantIsProvisionedOnce() {
        final var app = new SureVote(SampleStore.open());
        final var id = UUID.randomUUID();
        final var before = app.demoTenant();
        final var created = app.provisionDemoTenant(id, "Demo", "us-east");
        final var again = app.provisionDemoTenant(id, "Other", "us-west");
        final var shown = app
            .demoTenant()
            .orElseThrow();
        assertTrue(before.isEmpty());
        assertEquals(TenantKind.SHOWCASE, created.getKind());
        assertEquals(TenantStatus.ACTIVE, created.getStatus());
        assertEquals("Demo", again.getName());
        assertEquals(id, shown.getId());
        this.refused("demo tenant id is required", () -> app.provisionDemoTenant(null, "Demo", "us-east"));
    }

    @Test
    void theDemoTenantMustBeAShowcaseTenant() {
        final var world = SampleElection.fptp();
        this.refused(
            "the demo tenant must be a showcase tenant",
            () -> world.app.provisionDemoTenant(world.tenantId, "Demo", "us-east")
        );
    }

    @Test
    void aSuspendedTenantRecordsNoBallot() {
        final var world = SampleElection.fptp();
        final var sealed = world.sealed(world.slotA, "Y");
        world.app.updateSystemTenant(world.tenantId, null, TenantStatus.SUSPENDED, world.admin());
        this.refused("tenant is suspended", () -> world.app.importBallot(sealed));
        world.app.updateSystemTenant(world.tenantId, null, TenantStatus.ACTIVE, world.admin());
        world.app.importBallot(sealed);
    }

    private SystemAdmin admin(final UUID id, final String name) {
        final var admin = new SystemAdmin();
        admin.setId(id);
        admin.setName(name);
        return admin;
    }

    private Tenant tenant(final TenantKind kind, final TenantStatus status) {
        final var tenant = new Tenant();
        tenant.setName("County");
        tenant.setKind(kind);
        tenant.setResidencyRegion("us-east");
        tenant.setStatus(status);
        return tenant;
    }

    private void refused(final String message, final Executable body) {
        final var failure = assertThrows(ConstraintException.class, body);
        assertEquals(message, failure.getMessage());
    }
}
