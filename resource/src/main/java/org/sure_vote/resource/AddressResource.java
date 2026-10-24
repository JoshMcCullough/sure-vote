package org.sure_vote.resource;

import java.util.UUID;

public final class AddressResource {
    private UUID id;

    private UUID tenantId;

    private UUID localityId;

    private String street1;

    private String postalCode;

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

    public UUID getLocalityId() {
        return this.localityId;
    }

    public void setLocalityId(final UUID localityId) {
        this.localityId = localityId;
    }

    public String getStreet1() {
        return this.street1;
    }

    public void setStreet1(final String street1) {
        this.street1 = street1;
    }

    public String getPostalCode() {
        return this.postalCode;
    }

    public void setPostalCode(final String postalCode) {
        this.postalCode = postalCode;
    }
}
