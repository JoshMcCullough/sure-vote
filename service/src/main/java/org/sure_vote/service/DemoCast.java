package org.sure_vote.service;

public final class DemoCast {
    private final String code;
    private final LookupResult result;

    public DemoCast(final String code, final LookupResult result) {
        this.code = code;
        this.result = result;
    }

    public String code() {
        return this.code;
    }

    public LookupResult result() {
        return this.result;
    }
}
