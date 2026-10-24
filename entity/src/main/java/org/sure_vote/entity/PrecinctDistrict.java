package org.sure_vote.entity;

import java.util.UUID;

public final class PrecinctDistrict extends TenantEntity {
    private UUID votingPrecinctId;
    private UUID votingDistrictId;
    private DistrictType districtType;

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

    public DistrictType getDistrictType() {
        return this.districtType;
    }

    public void setDistrictType(final DistrictType districtType) {
        this.districtType = districtType;
    }
}
