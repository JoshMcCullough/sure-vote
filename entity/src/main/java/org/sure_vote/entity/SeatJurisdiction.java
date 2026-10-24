package org.sure_vote.entity;

public enum SeatJurisdiction implements WireValue {
    FEDERAL("federal"),
    STATE("state"),
    LOCAL("local");

    private final String wireValue;

    SeatJurisdiction(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
