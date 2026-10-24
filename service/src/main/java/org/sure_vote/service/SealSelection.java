package org.sure_vote.service;

import java.util.UUID;

public final class SealSelection {
    private final UUID raceCandidateId;
    private final String value;
    private final String nonce;

    public SealSelection(final UUID raceCandidateId, final String value, final String nonce) {
        this.raceCandidateId = raceCandidateId;
        this.value = value;
        this.nonce = nonce;
    }

    public UUID raceCandidateId() {
        return this.raceCandidateId;
    }

    public String value() {
        return this.value;
    }

    public String nonce() {
        return this.nonce;
    }
}
