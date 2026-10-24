package org.sure_vote.entity;

public enum RegistrationStatus implements WireValue {
    CURRENT("current"),
    HISTORICAL("historical"),
    EXPIRED("expired");

    private final String wireValue;

    RegistrationStatus(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
