package org.sure_vote.resource;

import java.util.List;

public final class OfficialListResource {
    private List<OfficialResource> officials;

    public List<OfficialResource> getOfficials() {
        return this.officials;
    }

    public void setOfficials(final List<OfficialResource> officials) {
        this.officials = officials;
    }
}
