package org.sure_vote.resource;

import java.util.UUID;

public final class PlaceResource {
    private UUID id;

    private UUID tenantId;

    private UUID localityId;

    private String name;

    private String kind;

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

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getKind() {
        return this.kind;
    }

    public void setKind(final String kind) {
        this.kind = kind;
    }
}
