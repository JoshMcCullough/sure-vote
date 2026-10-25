package org.sure_vote.entity;

public final class TenantOfficial extends TenantEntity {
    private String name;
    private TenantOfficialStatus status;

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public TenantOfficialStatus getStatus() {
        return this.status;
    }

    public void setStatus(final TenantOfficialStatus status) {
        this.status = status;
    }
}
