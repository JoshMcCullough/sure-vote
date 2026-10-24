package org.sure_vote.resource;

import java.util.UUID;

public final class VoteResource {
    private UUID id;
    private UUID raceCandidateId;
    private String value;
    private String hash;

    public UUID getId() {
        return this.id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public UUID getRaceCandidateId() {
        return this.raceCandidateId;
    }

    public void setRaceCandidateId(final UUID raceCandidateId) {
        this.raceCandidateId = raceCandidateId;
    }

    public String getValue() {
        return this.value;
    }

    public void setValue(final String value) {
        this.value = value;
    }

    public String getHash() {
        return this.hash;
    }

    public void setHash(final String hash) {
        this.hash = hash;
    }
}
