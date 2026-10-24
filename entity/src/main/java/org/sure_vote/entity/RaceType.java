package org.sure_vote.entity;

public enum RaceType implements WireValue {
    GENERAL("general"),
    PRIMARY("primary"),
    SPECIAL("special");

    private final String wireValue;

    RaceType(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
