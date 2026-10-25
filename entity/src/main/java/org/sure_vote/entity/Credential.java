package org.sure_vote.entity;

import java.time.Instant;
import java.util.UUID;

public final class Credential extends Entity {
    private PrincipalKind principalKind;
    private UUID principalId;
    private UUID tenantId;
    private String credentialId;
    private String userHandle;
    private String publicKeyCose;
    private Long signatureCount;
    private Instant lastUsedAt;

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

    public String getCredentialId() {
        return this.credentialId;
    }

    public void setCredentialId(final String credentialId) {
        this.credentialId = credentialId;
    }

    public String getUserHandle() {
        return this.userHandle;
    }

    public void setUserHandle(final String userHandle) {
        this.userHandle = userHandle;
    }

    public String getPublicKeyCose() {
        return this.publicKeyCose;
    }

    public void setPublicKeyCose(final String publicKeyCose) {
        this.publicKeyCose = publicKeyCose;
    }

    public Long getSignatureCount() {
        return this.signatureCount;
    }

    public void setSignatureCount(final Long signatureCount) {
        this.signatureCount = signatureCount;
    }

    public Instant getLastUsedAt() {
        return this.lastUsedAt;
    }

    public void setLastUsedAt(final Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }
}
