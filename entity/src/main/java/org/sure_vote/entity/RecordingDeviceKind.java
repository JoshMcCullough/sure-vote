package org.sure_vote.entity;

public enum RecordingDeviceKind implements WireValue {
    POLLING_PLACE_MACHINE("polling-place-machine"),
    CENTRAL_WORKSTATION("central-workstation");

    private final String wireValue;

    RecordingDeviceKind(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
