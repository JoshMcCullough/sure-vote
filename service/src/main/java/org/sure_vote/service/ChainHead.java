package org.sure_vote.service;

import java.util.UUID;

public final class ChainHead {
    private final UUID raceId;
    private final int length;
    private final String headHash;

    public ChainHead(final UUID raceId, final int length, final String headHash) {
        this.raceId = raceId;
        this.length = length;
        this.headHash = headHash;
    }

    public UUID raceId() {
        return this.raceId;
    }

    public int length() {
        return this.length;
    }

    public String headHash() {
        return this.headHash;
    }
}
