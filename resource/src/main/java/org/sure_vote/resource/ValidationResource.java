package org.sure_vote.resource;

import java.util.UUID;

public final class ValidationResource {
    private UUID id;
    private UUID tenantId;
    private UUID voteChainId;
    private UUID validatorId;
    private String dateValidated;
    private String validatedThroughHash;
    private int length;
    private String method;
    private String result;
    private String notes;
    private String evidenceHash;
    private String payload;
    private String hash;
    private String signature;

    public UUID getId() {
        return this.id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return this.tenantId;
    }

    public void setTenantId(final UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getVoteChainId() {
        return this.voteChainId;
    }

    public void setVoteChainId(final UUID voteChainId) {
        this.voteChainId = voteChainId;
    }

    public UUID getValidatorId() {
        return this.validatorId;
    }

    public void setValidatorId(final UUID validatorId) {
        this.validatorId = validatorId;
    }

    public String getDateValidated() {
        return this.dateValidated;
    }

    public void setDateValidated(final String dateValidated) {
        this.dateValidated = dateValidated;
    }

    public String getValidatedThroughHash() {
        return this.validatedThroughHash;
    }

    public void setValidatedThroughHash(final String validatedThroughHash) {
        this.validatedThroughHash = validatedThroughHash;
    }

    public int getLength() {
        return this.length;
    }

    public void setLength(final int length) {
        this.length = length;
    }

    public String getMethod() {
        return this.method;
    }

    public void setMethod(final String method) {
        this.method = method;
    }

    public String getResult() {
        return this.result;
    }

    public void setResult(final String result) {
        this.result = result;
    }

    public String getNotes() {
        return this.notes;
    }

    public void setNotes(final String notes) {
        this.notes = notes;
    }

    public String getEvidenceHash() {
        return this.evidenceHash;
    }

    public void setEvidenceHash(final String evidenceHash) {
        this.evidenceHash = evidenceHash;
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
