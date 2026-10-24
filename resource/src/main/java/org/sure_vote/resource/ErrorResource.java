package org.sure_vote.resource;

public final class ErrorResource {
    private final String error;

    public ErrorResource(final String error) {
        this.error = error;
    }

    public String getError() {
        return this.error;
    }
}
