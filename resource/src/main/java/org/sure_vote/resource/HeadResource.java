package org.sure_vote.resource;

import java.util.UUID;

public final class HeadResource {
    private UUID electionId;
    private UUID raceId;
    private int length;
    private String headHash;
    private String observedAt;
    private String signature;
    private String canvassHash;
    private boolean witnessGap;

    public UUID getElectionId() {
        return this.electionId;
    }

    public void setElectionId(final UUID electionId) {
        this.electionId = electionId;
    }

    public UUID getRaceId() {
        return this.raceId;
    }

    public void setRaceId(final UUID raceId) {
        this.raceId = raceId;
    }

    public int getLength() {
        return this.length;
    }

    public void setLength(final int length) {
        this.length = length;
    }

    public String getHeadHash() {
        return this.headHash;
    }

    public void setHeadHash(final String headHash) {
        this.headHash = headHash;
    }

    public String getObservedAt() {
        return this.observedAt;
    }

    public void setObservedAt(final String observedAt) {
        this.observedAt = observedAt;
    }

    public String getSignature() {
        return this.signature;
    }

    public void setSignature(final String signature) {
        this.signature = signature;
    }

    public String getCanvassHash() {
        return this.canvassHash;
    }

    public void setCanvassHash(final String canvassHash) {
        this.canvassHash = canvassHash;
    }

    public boolean getWitnessGap() {
        return this.witnessGap;
    }

    public void setWitnessGap(final boolean witnessGap) {
        this.witnessGap = witnessGap;
    }
}
