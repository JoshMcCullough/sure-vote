package org.sure_vote.entity;

public enum ElectionStatus implements WireValue {
    UPCOMING("upcoming"),
    ACTIVE("active"),
    HISTORICAL("historical");

    private final String wireValue;

    ElectionStatus(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
