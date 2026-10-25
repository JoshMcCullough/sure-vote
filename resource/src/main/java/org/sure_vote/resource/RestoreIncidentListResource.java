package org.sure_vote.resource;

import java.util.List;

public final class RestoreIncidentListResource {
    private List<RestoreIncidentResource> incidents;

    public List<RestoreIncidentResource> getIncidents() {
        return this.incidents;
    }

    public void setIncidents(final List<RestoreIncidentResource> incidents) {
        this.incidents = incidents;
    }
}
