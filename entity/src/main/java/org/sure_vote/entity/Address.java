package org.sure_vote.entity;

import java.util.UUID;

public final class Address extends TenantEntity {
    private String street1;
    private String street2;
    private UUID localityId;
    private String postalCode;
    private Double latitude;
    private Double longitude;

    public String getStreet1() {
        return this.street1;
    }

    public void setStreet1(final String street1) {
        this.street1 = street1;
    }

    public String getStreet2() {
        return this.street2;
    }

    public void setStreet2(final String street2) {
        this.street2 = street2;
    }

    public UUID getLocalityId() {
        return this.localityId;
    }

    public void setLocalityId(final UUID localityId) {
        this.localityId = localityId;
    }

    public String getPostalCode() {
        return this.postalCode;
    }

    public void setPostalCode(final String postalCode) {
        this.postalCode = postalCode;
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
}
