package org.sure_vote.entity;

public enum SeatType implements WireValue {
    PRESIDENT("president"),
    VICE_PRESIDENT("vice-president"),
    SENATOR("senator"),
    REPRESENTATIVE("representative"),
    GOVERNOR("governor"),
    MAYOR("mayor"),
    COUNCIL("council"),
    OTHER("other");

    private final String wireValue;

    SeatType(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
