package org.sure_vote.resource;

import java.util.UUID;

public final class PrecinctResource {
    private UUID id;

    private UUID tenantId;

    private String name;

    private UUID addressId;

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
