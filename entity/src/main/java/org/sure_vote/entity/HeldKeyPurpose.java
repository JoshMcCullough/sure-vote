package org.sure_vote.entity;

public enum HeldKeyPurpose implements WireValue {
    OPERATOR("operator"),
    IMPORT("import"),
    DEVICE("device"),
    VIEWING("viewing");

    private final String wireValue;

    HeldKeyPurpose(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
