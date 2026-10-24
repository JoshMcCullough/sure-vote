package org.sure_vote.entity;

import java.time.Instant;
import java.util.UUID;

public final class VoteChainEntry extends LedgerEntity {
    private UUID voteChainId;
    private UUID voteId;
    private UUID previousEntryId;
    private String payload;
    private String hash;
    private String signature;
    private int index;
    private Instant timestamp;
    private String nonce;

    public UUID getVoteChainId() {
        return this.voteChainId;
    }

    public void setVoteChainId(final UUID voteChainId) {
        this.voteChainId = voteChainId;
    }

    public UUID getVoteId() {
        return this.voteId;
    }

    public void setVoteId(final UUID voteId) {
        this.voteId = voteId;
    }

    public UUID getPreviousEntryId() {
        return this.previousEntryId;
    }

    public void setPreviousEntryId(final UUID previousEntryId) {
        this.previousEntryId = previousEntryId;
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

    public int getIndex() {
        return this.index;
    }

    public void setIndex(final int index) {
        this.index = index;
    }

    public Instant getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(final Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getNonce() {
        return this.nonce;
    }

    public void setNonce(final String nonce) {
        this.nonce = nonce;
    }
}
