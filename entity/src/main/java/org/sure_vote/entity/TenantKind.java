package org.sure_vote.entity;

public enum TenantKind implements WireValue {
    PRODUCTION("production"),
    SHOWCASE("showcase");

    private final String wireValue;

    TenantKind(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
