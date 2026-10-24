package org.sure_vote.entity;

public enum DistrictType implements WireValue {
    CONGRESSIONAL("congressional"),
    STATE_SENATE("state-senate"),
    STATE_HOUSE("state-house"),
    COUNTY_COMMISSION("county-commission"),
    CITY_COUNCIL("city-council"),
    SCHOOL_BOARD("school-board"),
    OTHER("other");

    private final String wireValue;

    DistrictType(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
