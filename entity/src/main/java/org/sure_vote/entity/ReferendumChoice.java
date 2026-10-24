package org.sure_vote.entity;

public enum ReferendumChoice implements WireValue {
    YES("yes"),
    NO("no");

    private final String wireValue;

    ReferendumChoice(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
