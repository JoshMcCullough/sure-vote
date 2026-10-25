package org.sure_vote.service;

import java.time.Instant;
import java.util.UUID;

import org.sure_vote.entity.ValidationResultStatus;

public final class ValidationRequest {
    private final UUID voteChainId;
    private final UUID validatorId;
    private final String validatedThroughHash;
    private final int length;
    private final ValidationResultStatus result;
    private final Instant dateValidated;
    private final String method;
    private final String notes;
    private final String evidenceHash;
    private final String signature;

    public ValidationRequest(
        final UUID voteChainId,
        final UUID validatorId,
        final String validatedThroughHash,
        final int length,
        final ValidationResultStatus result,
        final Instant dateValidated,
        final String method,
        final String notes,
        final String evidenceHash,
        final String signature
    ) {
        this.voteChainId = voteChainId;
        this.validatorId = validatorId;
        this.validatedThroughHash = validatedThroughHash;
        this.length = length;
        this.result = result;
        this.dateValidated = dateValidated;
        this.method = method;
        this.notes = notes;
        this.evidenceHash = evidenceHash;
        this.signature = signature;
    }

    public UUID voteChainId() {
        return this.voteChainId;
    }

    public UUID validatorId() {
        return this.validatorId;
    }

    public String validatedThroughHash() {
        return this.validatedThroughHash;
    }

    public int length() {
        return this.length;
    }

    public ValidationResultStatus result() {
        return this.result;
    }

    public Instant dateValidated() {
        return this.dateValidated;
    }

    public String method() {
        return this.method;
    }

    public String notes() {
        return this.notes;
    }

    public String evidenceHash() {
        return this.evidenceHash;
    }

    public String signature() {
        return this.signature;
    }
}
