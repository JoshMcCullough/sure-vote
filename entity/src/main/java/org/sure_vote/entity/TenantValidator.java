package org.sure_vote.entity;

import java.util.UUID;

public final class TenantValidator extends TenantEntity {
    private UUID validatorId;

    public UUID getValidatorId() {
        return this.validatorId;
    }

    public void setValidatorId(final UUID validatorId) {
        this.validatorId = validatorId;
    }
}
