package org.sure_vote.entity;

public enum OfficialStatus implements WireValue {
    ACTIVE("active"),
    INACTIVE("inactive"),
    IMPEACHED("impeached");

    private final String wireValue;

    OfficialStatus(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
