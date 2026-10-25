package org.sure_vote.service;

public final class AccessException extends RuntimeException {
    private final int status;

    public AccessException(final int status, final String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return this.status;
    }

    static AccessException signIn() {
        return new AccessException(401, "sign-in required");
    }

    static AccessException denied() {
        return new AccessException(403, "not allowed");
    }
}
