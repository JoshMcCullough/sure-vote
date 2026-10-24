package org.sure_vote.entity;

public enum BallotType implements WireValue {
    IN_PERSON("in-person"),
    MAIL_IN("mail-in"),
    ABSENTEE("absentee"),
    ONLINE("online");

    private final String wireValue;

    BallotType(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
