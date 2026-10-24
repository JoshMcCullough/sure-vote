package org.sure_vote.entity;

public enum IssuanceStatus implements WireValue {
    ACCEPTED("accepted"),
    VOIDED("voided");

    private final String wireValue;

    IssuanceStatus(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
