package org.sure_vote.entity;

public enum TenantOfficialStatus implements WireValue {
    ACTIVE("active"),
    DISABLED("disabled");

    private final String wireValue;

    TenantOfficialStatus(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
