package org.sure_vote.entity;

import java.util.UUID;

public final class VotingDistrict extends TenantEntity {
    private String name;
    private UUID localityId;
    private String boundary;
    private DistrictType type;
    private Integer districtNumber;

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public UUID getLocalityId() {
        return this.localityId;
    }

    public void setLocalityId(final UUID localityId) {
        this.localityId = localityId;
    }

    public String getBoundary() {
        return this.boundary;
    }

    public void setBoundary(final String boundary) {
        this.boundary = boundary;
    }

    public DistrictType getType() {
        return this.type;
    }

    public void setType(final DistrictType type) {
        this.type = type;
    }

    public Integer getDistrictNumber() {
        return this.districtNumber;
    }

    public void setDistrictNumber(final Integer districtNumber) {
        this.districtNumber = districtNumber;
    }
}
