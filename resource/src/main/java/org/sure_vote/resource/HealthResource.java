package org.sure_vote.resource;

public final class HealthResource {
    private final String status;

    public HealthResource(final String status) {
        this.status = status;
    }

    public String getStatus() {
        return this.status;
    }
}
