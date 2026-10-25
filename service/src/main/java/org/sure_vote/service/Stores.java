package org.sure_vote.service;

import java.time.Instant;
import java.util.UUID;

import org.sure_vote.entity.Entity;
import org.sure_vote.entity.Identifiable;
import org.sure_vote.entity.Tenant;
import org.sure_vote.entity.TenantStatus;
import org.sure_vote.repository.SureVoteRepository;

final class Stores {
    private final SureVoteRepository repository;

    Stores(final SureVoteRepository repository) {
        this.repository = repository;
    }

    SureVoteRepository repository() {
        return this.repository;
    }

    <T extends Identifiable> T require(final Class<T> type, final UUID id) {
        final var found = this.repository.find(type, id);
        if (found.isEmpty()) {
            throw new ConstraintException("Missing " + type.getSimpleName());
        }
        else {
            return found.get();
        }
    }

    void require(final boolean condition, final String message) {
        if (!condition) {
            throw new ConstraintException(message);
        }
    }

    void requireActiveTenant(final UUID tenantId) {
        final var tenant = this.require(Tenant.class, tenantId);
        this.require(tenant.getStatus() != TenantStatus.SUSPENDED, "tenant is suspended");
    }

    void sameTenant(final UUID expected, final UUID actual, final String label) {
        if (expected == null || !expected.equals(actual)) {
            throw new ConstraintException(label + " is in another tenant");
        }
    }

    <T extends Entity> T add(final T row, final UUID actor) {
        final var now = Instant.now();
        final var id = this.idOrNew(row.getId());
        this.require(actor != null, "actor is required");
        row.setId(id);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        row.setCreatedBy(actor);
        row.setUpdatedBy(actor);
        row.setVersion(0);
        return this.insert(row);
    }

    <T extends Identifiable> T insert(final T row) {
        try {
            return this.repository.insert(row);
        }
        catch (final IllegalArgumentException failure) {
            throw new ConstraintException(failure.getMessage());
        }
    }

    <T extends Identifiable> T replace(final T row) {
        try {
            return this.repository.replace(row);
        }
        catch (final IllegalArgumentException failure) {
            throw new ConstraintException(failure.getMessage());
        }
    }

    int nextVersion(final Integer version) {
        if (version == null) {
            return 1;
        }
        else {
            return version + 1;
        }
    }

    private UUID idOrNew(final UUID id) {
        if (id == null) {
            return UUID.randomUUID();
        }
        else {
            return id;
        }
    }
}
