package org.sure_vote.entity;

public enum PrincipalKind implements WireValue {
    SYSTEM_ADMIN("system-admin"),
    OFFICIAL("official"),
    VISITOR("visitor");

    private final String wireValue;

    PrincipalKind(final String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wire() {
        return this.wireValue;
    }
}
