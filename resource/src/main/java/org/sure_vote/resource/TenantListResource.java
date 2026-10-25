package org.sure_vote.resource;

import java.util.List;

public final class TenantListResource {
    private List<TenantResource> tenants;

    public List<TenantResource> getTenants() {
        return this.tenants;
    }

    public void setTenants(final List<TenantResource> tenants) {
        this.tenants = tenants;
    }
}
