package org.sure_vote.entity;

public enum IssuanceStatus implements WireValue {
    ISSUED("issued"),
    RECEIVED("received"),
    VOIDED("voided");

    private final String wireValue;

    IssuanceStatus(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }

    public boolean spends() {
        return this != VOIDED;
    }
}
