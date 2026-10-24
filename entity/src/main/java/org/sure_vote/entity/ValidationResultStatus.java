package org.sure_vote.entity;

public enum ValidationResultStatus implements WireValue {
    VALID("valid"),
    INVALID("invalid"),
    INCONCLUSIVE("inconclusive");

    private final String wireValue;

    ValidationResultStatus(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
