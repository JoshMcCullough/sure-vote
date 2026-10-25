package org.sure_vote.entity;

import java.time.Instant;
import java.util.UUID;

public final class Session extends Entity {
    private String tokenHash;
    private PrincipalKind principalKind;
    private UUID principalId;
    private UUID tenantId;
    private Instant expiresAt;
    private Instant lastSeenAt;
    private Instant revokedAt;

    public String getTokenHash() {
        return this.tokenHash;
    }

    public void setTokenHash(final String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public PrincipalKind getPrincipalKind() {
        return this.principalKind;
    }

    public void setPrincipalKind(final PrincipalKind principalKind) {
        this.principalKind = principalKind;
    }

    public UUID getPrincipalId() {
        return this.principalId;
    }

    public void setPrincipalId(final UUID principalId) {
        this.principalId = principalId;
    }

    public UUID getTenantId() {
        return this.tenantId;
    }

    public void setTenantId(final UUID tenantId) {
        this.tenantId = tenantId;
    }

    public Instant getExpiresAt() {
        return this.expiresAt;
    }

    public void setExpiresAt(final Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getLastSeenAt() {
        return this.lastSeenAt;
    }

    public void setLastSeenAt(final Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Instant getRevokedAt() {
        return this.revokedAt;
    }

    public void setRevokedAt(final Instant revokedAt) {
        this.revokedAt = revokedAt;
    }
}
