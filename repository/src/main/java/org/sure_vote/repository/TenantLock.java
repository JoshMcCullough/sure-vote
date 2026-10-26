package org.sure_vote.repository;

import java.util.Set;
import java.util.UUID;

final class TenantLock {
    private volatile Set<UUID> allowed = Set.of();

    void lock(final Set<UUID> tenantIds) {
        this.allowed = Set.copyOf(tenantIds);
    }

    void check(final UUID tenantId) {
        final var current = this.allowed;
        if (!current.isEmpty() && !current.contains(tenantId)) {
            throw new TenantLockedException();
        }
    }
}
