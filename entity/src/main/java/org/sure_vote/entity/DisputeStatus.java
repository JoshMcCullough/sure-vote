package org.sure_vote.entity;

public enum DisputeStatus implements WireValue {
    OPEN("open"),
    DISMISSED("dismissed"),
    NOTED("noted");

    private final String wireValue;

    DisputeStatus(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
