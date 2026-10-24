package org.sure_vote.entity;

public enum AssignmentStatus implements WireValue {
    CURRENT("current"),
    HISTORICAL("historical");

    private final String wireValue;

    AssignmentStatus(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
