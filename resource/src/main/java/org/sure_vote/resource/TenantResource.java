package org.sure_vote.resource;

import java.util.UUID;

public final class TenantResource {
    private UUID id;
    private String name;
    private String kind;
    private String residencyRegion;
    private String status;

    public UUID getId() {
        return this.id;
    }

    public void setId(final UUID id) {
        this.id = id;
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

    public String getResidencyRegion() {
        return this.residencyRegion;
    }

    public void setResidencyRegion(final String residencyRegion) {
        this.residencyRegion = residencyRegion;
    }

    public String getStatus() {
        return this.status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }
}
