package org.sure_vote.entity;

public enum CeremonyKind implements WireValue {
    REGISTRATION("registration"),
    ASSERTION("assertion");

    private final String wireValue;

    CeremonyKind(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
