package org.sure_vote.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.sure_vote.entity.Entity;
import org.sure_vote.entity.SystemAdmin;
import org.sure_vote.entity.SystemAdminStatus;
import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.TenantKind;
import org.sure_vote.entity.TenantStatus;
import org.sure_vote.repository.SureVoteRepository;

/**
 * Tenant management for system admins. An admin is global and is checked on every call.
 * Until request identity is chosen, the admin is the unsigned actor id.
 */
final class SystemAdminService {
    private final SureVoteRepository repository;
    private final Stores stores;
    private volatile UUID demoTenantId;

    SystemAdminService(final SureVoteRepository repository, final Stores stores) {
        this.repository = repository;
        this.stores = stores;
    }

    SystemAdmin bootstrap(final SystemAdmin admin) {
        this.stores.require(admin.getId() != null, "system admin id is required");
        return this.repository.asSystemAdmin(admin.getId(), () -> this.bootstrapped(admin));
    }

    SystemAdmin createAdmin(final SystemAdmin admin, final UUID actor) {
        return this.asAdmin(actor, () -> this.addAdmin(admin, actor));
    }

    List<SystemAdmin> admins(final UUID actor) {
        return this.asAdmin(actor, () -> this.repository.list(SystemAdmin.class));
    }

    SystemAdmin setAdminStatus(final UUID id, final SystemAdminStatus status, final UUID actor) {
        return this.asAdmin(actor, () -> this.changedAdmin(id, status, actor));
    }

    Tenant createTenant(final Tenant tenant, final UUID actor) {
        return this.asAdmin(actor, () -> this.addTenant(tenant, actor));
    }

    List<Tenant> tenants(final UUID actor) {
        return this.asAdmin(actor, () -> this.repository.list(Tenant.class));
    }

    Tenant updateTenant(final UUID id, final String name, final TenantStatus status, final UUID actor) {
        return this.asAdmin(actor, () -> this.changedTenant(id, name, status, actor));
    }

    Tenant provisionDemo(final UUID id, final String name, final String residencyRegion) {
        this.stores.require(id != null, "demo tenant id is required");
        return this.remembered(this.repository.inTenant(id, () -> this.demo(id, name, residencyRegion)));
    }

    private Tenant remembered(final Tenant tenant) {
        this.demoTenantId = tenant.getId();
        return tenant;
    }

    Optional<Tenant> demoTenant() {
        final var id = this.demoTenantId;
        if (id == null) {
            return Optional.empty();
        }
        else {
            return this.repository.inTenant(id, () -> this.repository.find(Tenant.class, id));
        }
    }

    private Tenant demo(final UUID id, final String name, final String residencyRegion) {
        final var existing = this.repository.find(Tenant.class, id);
        if (existing.isPresent()) {
            final var row = existing.get();
            this.stores.require(row.getKind() == TenantKind.SHOWCASE, "the demo tenant must be a showcase tenant");
            return row;
        }
        else {
            final var tenant = new Tenant();
            tenant.setId(id);
            tenant.setName(name);
            tenant.setKind(TenantKind.SHOWCASE);
            tenant.setResidencyRegion(residencyRegion);
            return this.addTenant(tenant, id);
        }
    }

    private <T> T asAdmin(final UUID actor, final Supplier<T> body) {
        this.stores.require(actor != null, "actor is required");
        return this.repository.asSystemAdmin(actor, () -> this.checked(actor, body));
    }

    private <T> T checked(final UUID actor, final Supplier<T> body) {
        final var admin = this.repository.find(SystemAdmin.class, actor);
        final var active = admin.isPresent() && this.active(admin.get());
        this.stores.require(active, "not a system admin");
        return body.get();
    }

    private boolean active(final SystemAdmin admin) {
        return admin.getStatus() == SystemAdminStatus.ACTIVE && admin.getDeletedAt() == null;
    }

    private SystemAdmin bootstrapped(final SystemAdmin admin) {
        final var existing = this.repository.find(SystemAdmin.class, admin.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        else {
            final var others = this.repository.list(SystemAdmin.class);
            this.stores.require(others.isEmpty(), "a system admin already exists");
            return this.addAdmin(admin, admin.getId());
        }
    }

    private SystemAdmin addAdmin(final SystemAdmin admin, final UUID actor) {
        this.requireName(admin.getName());
        admin.setStatus(this.or(admin.getStatus(), SystemAdminStatus.ACTIVE));
        return this.stores.add(admin, actor);
    }

    private SystemAdmin changedAdmin(final UUID id, final SystemAdminStatus status, final UUID actor) {
        final var existing = this.stores.require(SystemAdmin.class, id);
        final var next = Copies.systemAdmin(existing);
        this.stores.require(status != null, "system admin status is required");
        this.requireAnotherActive(id, status);
        next.setStatus(status);
        return this.replaced(existing, next, actor);
    }

    private void requireAnotherActive(final UUID id, final SystemAdminStatus status) {
        if (status == SystemAdminStatus.DISABLED) {
            final var admins = this.repository.list(SystemAdmin.class);
            final var stream = admins.stream();
            final var others = stream.filter(admin -> this.otherActive(admin, id));
            final var another = others.findAny();
            this.stores.require(another.isPresent(), "the last active system admin cannot be disabled");
        }
    }

    private boolean otherActive(final SystemAdmin admin, final UUID id) {
        final var other = !id.equals(admin.getId());
        return other && this.active(admin);
    }

    private Tenant addTenant(final Tenant tenant, final UUID actor) {
        this.requireName(tenant.getName());
        this.stores.require(tenant.getKind() != null, "tenant kind is required");
        tenant.setStatus(this.or(tenant.getStatus(), TenantStatus.ACTIVE));
        return this.stores.add(tenant, actor);
    }

    private Tenant changedTenant(final UUID id, final String name, final TenantStatus status, final UUID actor) {
        final var existing = this.stores.require(Tenant.class, id);
        final var next = Copies.tenant(existing);
        this.rename(next, name);
        next.setStatus(this.or(status, existing.getStatus()));
        return this.replaced(existing, next, actor);
    }

    private void rename(final Tenant tenant, final String name) {
        if (name != null) {
            this.requireName(name);
            tenant.setName(name.trim());
        }
    }

    private <T> T or(final T value, final T fallback) {
        if (value == null) {
            return fallback;
        }
        else {
            return value;
        }
    }

    private <T extends Entity> T replaced(final T existing, final T next, final UUID actor) {
        next.setUpdatedAt(Instant.now());
        next.setUpdatedBy(actor);
        next.setVersion(this.stores.nextVersion(existing.getVersion()));
        return this.stores.replace(next);
    }

    private void requireName(final String name) {
        this.stores.require(name != null && !name.isBlank(), "name is required");
    }
}
