package org.sure_vote.service;

import java.time.Instant;
import java.util.UUID;

public final class HeadNotice {
    private final UUID electionId;
    private final UUID raceId;
    private final int length;
    private final String headHash;
    private final Instant observedAt;
    private final String signature;
    private final String canvassHash;
    private final String bundleHash;
    private final boolean witnessGap;

    public HeadNotice(
        final UUID electionId,
        final UUID raceId,
        final int length,
        final String headHash,
        final Instant observedAt,
        final String signature,
        final String canvassHash,
        final String bundleHash,
        final boolean witnessGap
    ) {
        this.electionId = electionId;
        this.raceId = raceId;
        this.length = length;
        this.headHash = headHash;
        this.observedAt = observedAt;
        this.signature = signature;
        this.canvassHash = canvassHash;
        this.bundleHash = bundleHash;
        this.witnessGap = witnessGap;
    }

    public UUID electionId() {
        return this.electionId;
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

    public Instant observedAt() {
        return this.observedAt;
    }

    public String signature() {
        return this.signature;
    }

    public String canvassHash() {
        return this.canvassHash;
    }

    public String bundleHash() {
        return this.bundleHash;
    }

    public boolean witnessGap() {
        return this.witnessGap;
    }
}
