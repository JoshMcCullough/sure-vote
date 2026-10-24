package org.sure_vote.resource;

import java.util.UUID;

public final class PrecinctDistrictResource {
    private UUID id;

    private UUID tenantId;

    private UUID votingPrecinctId;

    private UUID votingDistrictId;

    private String districtType;

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

    public UUID getVotingPrecinctId() {
        return this.votingPrecinctId;
    }

    public void setVotingPrecinctId(final UUID votingPrecinctId) {
        this.votingPrecinctId = votingPrecinctId;
    }

    public UUID getVotingDistrictId() {
        return this.votingDistrictId;
    }

    public void setVotingDistrictId(final UUID votingDistrictId) {
        this.votingDistrictId = votingDistrictId;
    }

    public String getDistrictType() {
        return this.districtType;
    }

    public void setDistrictType(final String districtType) {
        this.districtType = districtType;
    }
}
