package org.sure_vote.entity;

public enum RecordingDeviceStatus implements WireValue {
    ACTIVE("active"),
    RETIRED("retired");

    private final String wireValue;

    RecordingDeviceStatus(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
