package org.sure_vote.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.sure_vote.entity.Enrollment;
import org.sure_vote.entity.PrincipalKind;
import org.sure_vote.entity.SystemAdmin;
import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.TenantOfficial;
import org.sure_vote.entity.TenantOfficialStatus;
import org.sure_vote.repository.SureVoteRepository;

final class OfficialService {
    static final Duration ENROLLMENT = Duration.ofHours(24);

    private final SureVoteRepository repository;
    private final Stores stores;
    private final SystemAdminService system;

    OfficialService(final SureVoteRepository repository, final Stores stores, final SystemAdminService system) {
        this.repository = repository;
        this.stores = stores;
        this.system = system;
    }

    OfficialEnrollment create(final UUID tenantId, final String name, final Principal actor) {
        return this.scoped(tenantId, actor, () -> this.added(tenantId, name, actor));
    }

    List<TenantOfficial> list(final UUID tenantId, final Principal actor) {
        return this.scoped(tenantId, actor, () -> this.inTenant(tenantId));
    }

    TenantOfficial setStatus(
        final UUID tenantId,
        final UUID id,
        final TenantOfficialStatus status,
        final Principal actor
    ) {
        this.stores.require(status != null, "official status is required");
        this.stores.require(!id.equals(actor.id()), "an official cannot change their own status");
        return this.scoped(tenantId, actor, () -> this.changed(tenantId, id, status, actor));
    }

    String enroll(final UUID tenantId, final UUID id, final Principal actor) {
        return this.scoped(tenantId, actor, () -> this.reenrolled(tenantId, id, actor));
    }

    String enrollAdmin(final UUID adminId, final Principal actor) {
        this.allowed(actor.admin());
        return this.system.asAdmin(actor.id(), () -> this.adminToken(adminId, actor.id()));
    }

    void bootstrap(final UUID adminId, final String token) {
        final var hash = Tokens.hash(token);
        final var id = Tokens.derived("enrollment", hash);
        final var existing = this.repository.find(Enrollment.class, id);
        if (existing.isEmpty()) {
            this.insert(id, hash, PrincipalKind.SYSTEM_ADMIN, adminId, null, adminId);
        }
    }

    String issue(final PrincipalKind kind, final UUID id, final UUID tenantId, final UUID actor) {
        final var token = Tokens.random();
        final var hash = Tokens.hash(token);
        this.insert(Tokens.derived("enrollment", hash), hash, kind, id, tenantId, actor);
        return token;
    }

    private void insert(
        final UUID id,
        final String hash,
        final PrincipalKind kind,
        final UUID principalId,
        final UUID tenantId,
        final UUID actor
    ) {
        final var enrollment = new Enrollment();
        final var now = Instant.now();
        enrollment.setId(id);
        enrollment.setTokenHash(hash);
        enrollment.setPrincipalKind(kind);
        enrollment.setPrincipalId(principalId);
        enrollment.setTenantId(tenantId);
        enrollment.setExpiresAt(now.plus(ENROLLMENT));
        this.stores.add(enrollment, actor);
    }

    private <T> T scoped(final UUID tenantId, final Principal actor, final Supplier<T> body) {
        this.stores.require(tenantId != null, "tenant is required");
        if (actor.admin()) {
            return this.system.asAdmin(actor.id(), () -> this.repository.inTenant(tenantId, body));
        }
        else {
            this.allowed(actor.kind() == PrincipalKind.OFFICIAL && tenantId.equals(actor.tenantId()));
            return this.repository.inTenant(tenantId, body);
        }
    }

    private OfficialEnrollment added(final UUID tenantId, final String name, final Principal actor) {
        final var official = new TenantOfficial();
        this.stores.require(Tenant.class, tenantId);
        this.stores.require(name != null && !name.isBlank(), "name is required");
        official.setTenantId(tenantId);
        official.setName(name.trim());
        official.setStatus(TenantOfficialStatus.ACTIVE);
        this.stores.add(official, actor.id());
        return this.enrolled(official, actor);
    }

    private OfficialEnrollment enrolled(final TenantOfficial official, final Principal actor) {
        final var token = this.issue(PrincipalKind.OFFICIAL, official.getId(), official.getTenantId(), actor.id());
        return new OfficialEnrollment(official, token);
    }

    private List<TenantOfficial> inTenant(final UUID tenantId) {
        final var rows = this.repository.list(TenantOfficial.class);
        final var stream = rows.stream();
        return stream
            .filter(row -> tenantId.equals(row.getTenantId()))
            .toList();
    }

    private TenantOfficial changed(
        final UUID tenantId,
        final UUID id,
        final TenantOfficialStatus status,
        final Principal actor
    ) {
        final var existing = this.official(tenantId, id);
        final var next = new TenantOfficial();
        next.setId(existing.getId());
        next.setCreatedAt(existing.getCreatedAt());
        next.setCreatedBy(existing.getCreatedBy());
        next.setTenantId(existing.getTenantId());
        next.setName(existing.getName());
        next.setStatus(status);
        next.setUpdatedAt(Instant.now());
        next.setUpdatedBy(actor.id());
        next.setVersion(this.stores.nextVersion(existing.getVersion()));
        return this.stores.replace(next);
    }

    private String reenrolled(final UUID tenantId, final UUID id, final Principal actor) {
        final var official = this.official(tenantId, id);
        this.stores.require(official.getStatus() == TenantOfficialStatus.ACTIVE, "the official is disabled");
        return this.issue(PrincipalKind.OFFICIAL, id, tenantId, actor.id());
    }

    private TenantOfficial official(final UUID tenantId, final UUID id) {
        final var official = this.stores.require(TenantOfficial.class, id);
        this.stores.sameTenant(tenantId, official.getTenantId(), "Official");
        return official;
    }

    private String adminToken(final UUID adminId, final UUID actor) {
        this.stores.require(SystemAdmin.class, adminId);
        return this.issue(PrincipalKind.SYSTEM_ADMIN, adminId, null, actor);
    }

    private void allowed(final boolean condition) {
        if (!condition) {
            throw AccessException.denied();
        }
    }
}
