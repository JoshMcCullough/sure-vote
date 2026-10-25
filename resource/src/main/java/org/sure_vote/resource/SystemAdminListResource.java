package org.sure_vote.resource;

import java.util.List;

public final class SystemAdminListResource {
    private List<SystemAdminResource> admins;

    public List<SystemAdminResource> getAdmins() {
        return this.admins;
    }

    public void setAdmins(final List<SystemAdminResource> admins) {
        this.admins = admins;
    }
}
