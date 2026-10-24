package org.sure_vote.entity;

import java.util.UUID;

public final class VotingPrecinct extends TenantEntity {
    private String name;
    private UUID addressId;

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public UUID getAddressId() {
        return this.addressId;
    }

    public void setAddressId(final UUID addressId) {
        this.addressId = addressId;
    }
}
