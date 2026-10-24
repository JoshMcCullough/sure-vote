package org.sure_vote.entity;

public final class Tenant extends TenantEntity {
    private String name;
    private TenantKind kind;
    private String residencyRegion;
    private TenantStatus status;

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public TenantKind getKind() {
        return this.kind;
    }

    public void setKind(final TenantKind kind) {
        this.kind = kind;
    }

    public String getResidencyRegion() {
        return this.residencyRegion;
    }

    public void setResidencyRegion(final String residencyRegion) {
        this.residencyRegion = residencyRegion;
    }

    public TenantStatus getStatus() {
        return this.status;
    }

    public void setStatus(final TenantStatus status) {
        this.status = status;
    }
}
