package org.sure_vote.entity;

import java.util.UUID;

public final class VoteChain extends TenantEntity {
    private UUID raceId;
    private ChainStatus status;
    private UUID initialEntryId;
    private UUID latestEntryId;
    private int length;
    private String hash;
    private String signature;

    public UUID getRaceId() {
        return this.raceId;
    }

    public void setRaceId(final UUID raceId) {
        this.raceId = raceId;
    }

    public ChainStatus getStatus() {
        return this.status;
    }

    public void setStatus(final ChainStatus status) {
        this.status = status;
    }

    public UUID getInitialEntryId() {
        return this.initialEntryId;
    }

    public void setInitialEntryId(final UUID initialEntryId) {
        this.initialEntryId = initialEntryId;
    }

    public UUID getLatestEntryId() {
        return this.latestEntryId;
    }

    public void setLatestEntryId(final UUID latestEntryId) {
        this.latestEntryId = latestEntryId;
    }

    public int getLength() {
        return this.length;
    }

    public void setLength(final int length) {
        this.length = length;
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
