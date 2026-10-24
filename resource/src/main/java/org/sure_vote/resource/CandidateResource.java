package org.sure_vote.resource;

import java.util.UUID;

public final class CandidateResource {
    private UUID id;

    private UUID tenantId;

    private UUID personId;

    private String partyAffiliation;

    public UUID getId() {
        return this.id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return this.tenantId;
    }

    public void setTenantId(final UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getPersonId() {
        return this.personId;
    }

    public void setPersonId(final UUID personId) {
        this.personId = personId;
    }

    public String getPartyAffiliation() {
        return this.partyAffiliation;
    }

    public void setPartyAffiliation(final String partyAffiliation) {
        this.partyAffiliation = partyAffiliation;
    }
}
