package org.sure_vote.entity;

import java.time.Instant;
import java.util.UUID;

public final class AuthCeremony extends Entity {
    private CeremonyKind kind;
    private String request;
    private UUID enrollmentId;
    private PrincipalKind principalKind;
    private UUID principalId;
    private UUID tenantId;
    private Instant expiresAt;
    private Instant usedAt;

    public CeremonyKind getKind() {
        return this.kind;
    }

    public void setKind(final CeremonyKind kind) {
        this.kind = kind;
    }

    public String getRequest() {
        return this.request;
    }

    public void setRequest(final String request) {
        this.request = request;
    }

    public UUID getEnrollmentId() {
        return this.enrollmentId;
    }

    public void setEnrollmentId(final UUID enrollmentId) {
        this.enrollmentId = enrollmentId;
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
