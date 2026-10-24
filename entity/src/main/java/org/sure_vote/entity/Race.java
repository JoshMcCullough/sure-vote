package org.sure_vote.entity;

import java.util.UUID;

public final class Race extends TenantEntity {
    private String name;
    private UUID electionId;
    private UUID termId;
    private UUID previousRaceId;
    private RaceType type;
    private ElectionMethod method;
    private int round;
    private String description;

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
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

    public UUID getPreviousRaceId() {
        return this.previousRaceId;
    }

    public void setPreviousRaceId(final UUID previousRaceId) {
        this.previousRaceId = previousRaceId;
    }

    public RaceType getType() {
        return this.type;
    }

    public void setType(final RaceType type) {
        this.type = type;
    }

    public ElectionMethod getMethod() {
        return this.method;
    }

    public void setMethod(final ElectionMethod method) {
        this.method = method;
    }

    public int getRound() {
        return this.round;
    }

    public void setRound(final int round) {
        this.round = round;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }
}
