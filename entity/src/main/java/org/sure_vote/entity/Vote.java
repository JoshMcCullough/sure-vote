package org.sure_vote.entity;

import java.util.UUID;

public final class Vote extends LedgerEntity {
    private UUID ballotId;
    private UUID raceCandidateId;
    private String value;
    private String payload;
    private String hash;
    private String signature;

    public UUID getBallotId() {
        return this.ballotId;
    }

    public void setBallotId(final UUID ballotId) {
        this.ballotId = ballotId;
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

    public String getPayload() {
        return this.payload;
    }

    public void setPayload(final String payload) {
        this.payload = payload;
    }

    public String getHash() {
        return this.hash;
    }

    public void setHash(final String hash) {
        this.hash = hash;
    }

    public String getSignature() {
        return this.signature;
    }

    public void setSignature(final String signature) {
        this.signature = signature;
    }
}
