package org.sure_vote.resource;

import java.util.UUID;

public final class TenantValidatorResource {
    private UUID id;
    private UUID tenantId;
    private UUID validatorId;

    public UUID getId() {
        return this.id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return this.tenantId;
    }

    public void setTenantId(final UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getValidatorId() {
        return this.validatorId;
    }

    public void setValidatorId(final UUID validatorId) {
        this.validatorId = validatorId;
    }
}
