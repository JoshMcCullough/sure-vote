package org.sure_vote.entity;

import java.util.UUID;

public final class Locality extends TenantEntity {
    private String name;
    private String abbreviation;
    private String type;
    private UUID parentId;
    private String fipsCode;
    private Integer censusPopulation;
    private Integer censusYear;
    private Double latitude;
    private Double longitude;
    private String boundary;

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getAbbreviation() {
        return this.abbreviation;
    }

    public void setAbbreviation(final String abbreviation) {
        this.abbreviation = abbreviation;
    }

    public String getType() {
        return this.type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public UUID getParentId() {
        return this.parentId;
    }

    public void setParentId(final UUID parentId) {
        this.parentId = parentId;
    }

    public String getFipsCode() {
        return this.fipsCode;
    }

    public void setFipsCode(final String fipsCode) {
        this.fipsCode = fipsCode;
    }

    public Integer getCensusPopulation() {
        return this.censusPopulation;
    }

    public void setCensusPopulation(final Integer censusPopulation) {
        this.censusPopulation = censusPopulation;
    }

    public Integer getCensusYear() {
        return this.censusYear;
    }

    public void setCensusYear(final Integer censusYear) {
        this.censusYear = censusYear;
    }

    public Double getLatitude() {
        return this.latitude;
    }

    public void setLatitude(final Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return this.longitude;
    }

    public void setLongitude(final Double longitude) {
        this.longitude = longitude;
    }

    public String getBoundary() {
        return this.boundary;
    }

    public void setBoundary(final String boundary) {
        this.boundary = boundary;
    }
}
