package org.sure_vote.entity;

public enum SystemAdminStatus implements WireValue {
    ACTIVE("active"),
    DISABLED("disabled");

    private final String wireValue;

    SystemAdminStatus(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
