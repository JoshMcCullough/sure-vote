package org.sure_vote.entity;

import java.time.Instant;
import java.util.UUID;

public abstract class LedgerEntity implements Identifiable {
    private UUID id;
    private Instant createdAt;
    private UUID createdBy;
    private UUID tenantId;

    @Override
    public UUID getId() {
        return this.id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public void setCreatedAt(final Instant createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getCreatedBy() {
        return this.createdBy;
    }

    public void setCreatedBy(final UUID createdBy) {
        this.createdBy = createdBy;
    }

    public UUID getTenantId() {
        return this.tenantId;
    }

    public void setTenantId(final UUID tenantId) {
        this.tenantId = tenantId;
    }
}
