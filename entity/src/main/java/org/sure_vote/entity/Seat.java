package org.sure_vote.entity;

import java.util.UUID;

public final class Seat extends TenantEntity {
    private String name;
    private UUID localityId;
    private SeatJurisdiction jurisdiction;
    private SeatType type;
    private String description;
    private int appointments;
    private Integer districtNumber;
    private UUID votingDistrictId;
    private int termLength;

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

    public SeatJurisdiction getJurisdiction() {
        return this.jurisdiction;
    }

    public void setJurisdiction(final SeatJurisdiction jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public SeatType getType() {
        return this.type;
    }

    public void setType(final SeatType type) {
        this.type = type;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public int getAppointments() {
        return this.appointments;
    }

    public void setAppointments(final int appointments) {
        this.appointments = appointments;
    }

    public Integer getDistrictNumber() {
        return this.districtNumber;
    }

    public void setDistrictNumber(final Integer districtNumber) {
        this.districtNumber = districtNumber;
    }

    public UUID getVotingDistrictId() {
        return this.votingDistrictId;
    }

    public void setVotingDistrictId(final UUID votingDistrictId) {
        this.votingDistrictId = votingDistrictId;
    }

    public int getTermLength() {
        return this.termLength;
    }

    public void setTermLength(final int termLength) {
        this.termLength = termLength;
    }
}
