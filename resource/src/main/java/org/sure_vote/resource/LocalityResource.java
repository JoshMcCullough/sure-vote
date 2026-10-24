package org.sure_vote.resource;

import java.util.UUID;

public final class LocalityResource {
    private UUID id;

    private UUID tenantId;

    private String name;

    private String type;

    private UUID parentId;

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

    public String getType() {
        return this.type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public UUID getParentId() {
        return this.parentId;
    }

    public void setParentId(final UUID parentId) {
        this.parentId = parentId;
    }
}
