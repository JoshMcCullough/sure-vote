package org.sure_vote.resource;

import java.util.UUID;

public final class SeatResource {
    private UUID id;

    private UUID tenantId;

    private UUID localityId;

    private String name;

    private String jurisdiction;

    private String type;

    private int appointments;

    private int termLength;

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

    public String getJurisdiction() {
        return this.jurisdiction;
    }

    public void setJurisdiction(final String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public String getType() {
        return this.type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public int getAppointments() {
        return this.appointments;
    }

    public void setAppointments(final int appointments) {
        this.appointments = appointments;
    }

    public int getTermLength() {
        return this.termLength;
    }

    public void setTermLength(final int termLength) {
        this.termLength = termLength;
    }
}
