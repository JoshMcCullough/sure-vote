package org.sure_vote.entity;

public enum TenantStatus implements WireValue {
    ACTIVE("active"),
    SUSPENDED("suspended");

    private final String wireValue;

    TenantStatus(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
