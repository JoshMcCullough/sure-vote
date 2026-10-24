package org.sure_vote.service;

import java.time.Instant;
import java.util.UUID;

public final class ImportVote {
    private final UUID raceCandidateId;
    private final String value;
    private final String signature;
    private final Instant entryTimestamp;
    private final String nonce;
    private final String entrySignature;

    public ImportVote(
        final UUID raceCandidateId,
        final String value,
        final String signature,
        final Instant entryTimestamp,
        final String nonce,
        final String entrySignature
    ) {
        this.raceCandidateId = raceCandidateId;
        this.value = value;
        this.signature = signature;
        this.entryTimestamp = entryTimestamp;
        this.nonce = nonce;
        this.entrySignature = entrySignature;
    }

    public UUID raceCandidateId() {
        return this.raceCandidateId;
    }

    public String value() {
        return this.value;
    }

    public String signature() {
        return this.signature;
    }

    public Instant entryTimestamp() {
        return this.entryTimestamp;
    }

    public String nonce() {
        return this.nonce;
    }

    public String entrySignature() {
        return this.entrySignature;
    }
}
