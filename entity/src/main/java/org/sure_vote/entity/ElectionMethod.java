package org.sure_vote.entity;

public enum ElectionMethod implements WireValue {
    FIRST_PAST_THE_POST("first-past-the-post"),
    REFERENDUM("referendum"),
    RANKED_CHOICE("ranked-choice");

    private final String wireValue;

    ElectionMethod(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
