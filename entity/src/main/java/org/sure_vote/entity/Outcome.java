package org.sure_vote.entity;

public enum Outcome implements WireValue {
    WON("won"),
    LOST("lost"),
    ELIMINATED("eliminated"),
    TIED("tied");

    private final String wireValue;

    Outcome(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
