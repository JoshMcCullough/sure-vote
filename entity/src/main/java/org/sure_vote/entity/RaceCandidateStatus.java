package org.sure_vote.entity;

public enum RaceCandidateStatus implements WireValue {
    ACTIVE("active"),
    WITHDRAWN("withdrawn");

    private final String wireValue;

    RaceCandidateStatus(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
