package org.sure_vote.repository;

public final class TenantLockedException extends RuntimeException {
    public TenantLockedException() {
        super("tenant is outside this deployment");
    }
}
