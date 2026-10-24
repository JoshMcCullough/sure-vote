package org.sure_vote.entity;

public enum RecordingPlaceKind implements WireValue {
    POLLING_PLACE("polling-place"),
    CENTRAL_OFFICE("central-office");

    private final String wireValue;

    RecordingPlaceKind(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
