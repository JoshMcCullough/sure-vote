package org.sure_vote.service;

import java.util.UUID;

import org.sure_vote.entity.PrincipalKind;

public record Principal(PrincipalKind kind, UUID id, UUID tenantId, String name) {
    public boolean admin() {
        return this.kind == PrincipalKind.SYSTEM_ADMIN;
    }

    public boolean inTenant() {
        return this.kind == PrincipalKind.OFFICIAL || this.kind == PrincipalKind.VISITOR;
    }
}
