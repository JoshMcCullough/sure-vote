package org.sure_vote.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.sure_vote.entity.PrincipalKind;
import org.sure_vote.entity.Session;
import org.sure_vote.entity.SystemAdmin;
import org.sure_vote.entity.SystemAdminStatus;
import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.TenantKind;
import org.sure_vote.entity.TenantOfficial;
import org.sure_vote.entity.TenantOfficialStatus;
import org.sure_vote.entity.TenantStatus;
import org.sure_vote.repository.SureVoteRepository;

final class SessionService {
    static final Duration LIFETIME = Duration.ofHours(12);
    static final Duration IDLE = Duration.ofHours(1);
    private static final Duration TOUCH = Duration.ofMinutes(5);

    private final SureVoteRepository repository;
    private final Stores stores;

    SessionService(final SureVoteRepository repository, final Stores stores) {
        this.repository = repository;
        this.stores = stores;
    }

    String open(final Principal principal) {
        final var token = Tokens.random();
        final var hash = Tokens.hash(token);
        final var now = Instant.now();
        final var session = new Session();
        session.setId(Tokens.derived("session", hash));
        session.setTokenHash(hash);
        session.setPrincipalKind(principal.kind());
        session.setPrincipalId(principal.id());
        session.setTenantId(principal.tenantId());
        session.setExpiresAt(now.plus(LIFETIME));
        session.setLastSeenAt(now);
        this.stores.add(session, principal.id());
        return token;
    }

    Principal resolve(final String token) {
        final var session = this.live(token);
        final var kind = session.getPrincipalKind();
        final var principal = this.principal(kind, session.getPrincipalId(), session.getTenantId());
        this.touch(session);
        return principal;
    }

    void revoke(final String token) {
        final var found = this.find(token);
        if (found.isPresent()) {
            final var session = found.get();
            final var next = this.copy(session);
            next.setRevokedAt(Instant.now());
            this.replaced(session, next);
        }
    }

    Principal principal(final PrincipalKind kind, final UUID id, final UUID tenantId) {
        if (kind == PrincipalKind.SYSTEM_ADMIN) {
            return this.repository.asSystemAdmin(id, () -> this.admin(id));
        }
        else if (kind == PrincipalKind.OFFICIAL) {
            return this.repository.inTenant(tenantId, () -> this.official(id, tenantId));
        }
        else {
            return this.repository.inTenant(tenantId, () -> this.visitor(id, tenantId));
        }
    }

    Principal visitor(final UUID tenantId) {
        return this.repository.inTenant(tenantId, () -> this.visitor(UUID.randomUUID(), tenantId));
    }

    private Session live(final String token) {
        final var found = this.find(token);
        final var now = Instant.now();
        if (found.isEmpty()) {
            throw AccessException.signIn();
        }
        else {
            final var session = found.get();
            final var lastSeen = session.getLastSeenAt();
            final var idleUntil = lastSeen.plus(IDLE);
            final var revoked = session.getRevokedAt() != null;
            final var expired = !now.isBefore(session.getExpiresAt()) || !now.isBefore(idleUntil);
            this.signedIn(!revoked && !expired);
            return session;
        }
    }

    private Optional<Session> find(final String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        else {
            final var hash = Tokens.hash(token);
            final var found = this.repository.find(Session.class, Tokens.derived("session", hash));
            return found.filter(session -> hash.equals(session.getTokenHash()));
        }
    }

    private void touch(final Session session) {
        final var now = Instant.now();
        final var lastSeen = session.getLastSeenAt();
        final var due = lastSeen.plus(TOUCH);
        if (now.isAfter(due)) {
            final var next = this.copy(session);
            next.setLastSeenAt(now);
            this.replaced(session, next);
        }
    }

    private Principal admin(final UUID id) {
        final var found = this.repository.find(SystemAdmin.class, id);
        final var admin = found.filter(row -> row.getStatus() == SystemAdminStatus.ACTIVE
            && row.getDeletedAt() == null);
        final var name = admin.map(SystemAdmin::getName);
        return new Principal(PrincipalKind.SYSTEM_ADMIN, id, null, name.orElseThrow(AccessException::signIn));
    }

    private Principal official(final UUID id, final UUID tenantId) {
        final var found = this.repository.find(TenantOfficial.class, id);
        final var official = found.filter(row -> this.activeOfficial(row, tenantId));
        final var name = official.map(TenantOfficial::getName);
        return new Principal(PrincipalKind.OFFICIAL, id, tenantId, name.orElseThrow(AccessException::signIn));
    }

    private boolean activeOfficial(final TenantOfficial row, final UUID tenantId) {
        final var active = row.getStatus() == TenantOfficialStatus.ACTIVE && row.getDeletedAt() == null;
        return active && tenantId.equals(row.getTenantId());
    }

    private Principal visitor(final UUID id, final UUID tenantId) {
        final var found = this.repository.find(Tenant.class, tenantId);
        final var tenant = found.filter(row -> row.getKind() == TenantKind.SHOWCASE);
        final var open = tenant.filter(row -> row.getStatus() != TenantStatus.SUSPENDED);
        this.signedIn(open.isPresent());
        return new Principal(PrincipalKind.VISITOR, id, tenantId, "Visitor");
    }

    private void signedIn(final boolean condition) {
        if (!condition) {
            throw AccessException.signIn();
        }
    }

    private Session copy(final Session source) {
        final var next = new Session();
        next.setId(source.getId());
        next.setCreatedAt(source.getCreatedAt());
        next.setCreatedBy(source.getCreatedBy());
        next.setTokenHash(source.getTokenHash());
        next.setPrincipalKind(source.getPrincipalKind());
        next.setPrincipalId(source.getPrincipalId());
        next.setTenantId(source.getTenantId());
        next.setExpiresAt(source.getExpiresAt());
        next.setLastSeenAt(source.getLastSeenAt());
        next.setRevokedAt(source.getRevokedAt());
        return next;
    }

    private void replaced(final Session existing, final Session next) {
        next.setUpdatedAt(Instant.now());
        next.setUpdatedBy(existing.getPrincipalId());
        next.setVersion(this.stores.nextVersion(existing.getVersion()));
        this.stores.replace(next);
    }
}
