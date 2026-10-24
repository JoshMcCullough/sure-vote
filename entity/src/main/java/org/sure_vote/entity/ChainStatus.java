package org.sure_vote.entity;

public enum ChainStatus implements WireValue {
    ACTIVE("active"),
    CLOSED("closed");

    private final String wireValue;

    ChainStatus(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
