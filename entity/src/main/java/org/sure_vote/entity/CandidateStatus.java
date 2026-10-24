package org.sure_vote.entity;

public enum CandidateStatus implements WireValue {
    ACTIVE("active"),
    WITHDRAWN("withdrawn"),
    HISTORICAL("historical");

    private final String wireValue;

    CandidateStatus(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
