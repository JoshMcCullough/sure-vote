package org.sure_vote.resource;

import java.util.UUID;

public final class VoteResource {
    private UUID id;
    private UUID raceCandidateId;
    private String value;
    private String hash;
    private UUID raceId;
    private String raceName;
    private String method;
    private String candidateName;
    private Boolean writeIn;
    private String referendumChoice;

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

    public UUID getRaceId() {
        return this.raceId;
    }

    public void setRaceId(final UUID raceId) {
        this.raceId = raceId;
    }

    public String getRaceName() {
        return this.raceName;
    }

    public void setRaceName(final String raceName) {
        this.raceName = raceName;
    }

    public String getMethod() {
        return this.method;
    }

    public void setMethod(final String method) {
        this.method = method;
    }

    public String getCandidateName() {
        return this.candidateName;
    }

    public void setCandidateName(final String candidateName) {
        this.candidateName = candidateName;
    }

    public Boolean getWriteIn() {
        return this.writeIn;
    }

    public void setWriteIn(final Boolean writeIn) {
        this.writeIn = writeIn;
    }

    public String getReferendumChoice() {
        return this.referendumChoice;
    }

    public void setReferendumChoice(final String referendumChoice) {
        this.referendumChoice = referendumChoice;
    }
}
