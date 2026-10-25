package org.sure_vote.entity;

import java.time.Instant;
import java.util.UUID;

public final class Enrollment extends Entity {
    private String tokenHash;
    private PrincipalKind principalKind;
    private UUID principalId;
    private UUID tenantId;
    private Instant expiresAt;
    private Instant usedAt;

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

    public Instant getUsedAt() {
        return this.usedAt;
    }

    public void setUsedAt(final Instant usedAt) {
        this.usedAt = usedAt;
    }
}
