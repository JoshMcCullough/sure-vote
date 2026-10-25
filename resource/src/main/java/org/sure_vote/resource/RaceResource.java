package org.sure_vote.resource;

import java.util.UUID;

public final class RaceResource {
    private UUID id;

    private UUID tenantId;

    private UUID electionId;

    private UUID termId;

    private String name;

    private String type;

    private String method;

    private UUID previousRaceId;

    private int round;

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

    public UUID getElectionId() {
        return this.electionId;
    }

    public void setElectionId(final UUID electionId) {
        this.electionId = electionId;
    }

    public UUID getTermId() {
        return this.termId;
    }

    public void setTermId(final UUID termId) {
        this.termId = termId;
    }

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getType() {
        return this.type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public String getMethod() {
        return this.method;
    }

    public void setMethod(final String method) {
        this.method = method;
    }

    public UUID getPreviousRaceId() {
        return this.previousRaceId;
    }

    public void setPreviousRaceId(final UUID previousRaceId) {
        this.previousRaceId = previousRaceId;
    }

    public int getRound() {
        return this.round;
    }

    public void setRound(final int round) {
        this.round = round;
    }
}
