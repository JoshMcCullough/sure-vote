package org.sure_vote.entity;

import java.util.UUID;

public abstract class TenantEntity extends Entity {
    private UUID tenantId;

    public UUID getTenantId() {
        return this.tenantId;
    }

    public void setTenantId(final UUID tenantId) {
        this.tenantId = tenantId;
    }
}
