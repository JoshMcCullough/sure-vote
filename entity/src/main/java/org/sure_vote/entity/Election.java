package org.sure_vote.entity;

import java.time.Instant;
import java.util.UUID;

public final class Election extends TenantEntity {
    private UUID localityId;
    private String name;
    private String description;
    private ElectionStatus status;
    private Instant votingStart;
    private Instant votingEnd;
    private String ballotHashPayloadSchema;
    private String voteHashPayloadSchema;
    private String voteChainEntryHashPayloadSchema;
    private String hashAlgorithm;
    private String operatorPublicKey;
    private String importPublicKey;
    private String viewingPublicKey;
    private String hash;
    private String signature;
    private Instant frozenAt;

    public UUID getLocalityId() {
        return this.localityId;
    }

    public void setLocalityId(final UUID localityId) {
        this.localityId = localityId;
    }

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public ElectionStatus getStatus() {
        return this.status;
    }

    public void setStatus(final ElectionStatus status) {
        this.status = status;
    }

    public Instant getVotingStart() {
        return this.votingStart;
    }

    public void setVotingStart(final Instant votingStart) {
        this.votingStart = votingStart;
    }

    public Instant getVotingEnd() {
        return this.votingEnd;
    }

    public void setVotingEnd(final Instant votingEnd) {
        this.votingEnd = votingEnd;
    }

    public String getBallotHashPayloadSchema() {
        return this.ballotHashPayloadSchema;
    }

    public void setBallotHashPayloadSchema(final String ballotHashPayloadSchema) {
        this.ballotHashPayloadSchema = ballotHashPayloadSchema;
    }

    public String getVoteHashPayloadSchema() {
        return this.voteHashPayloadSchema;
    }

    public void setVoteHashPayloadSchema(final String voteHashPayloadSchema) {
        this.voteHashPayloadSchema = voteHashPayloadSchema;
    }

    public String getVoteChainEntryHashPayloadSchema() {
        return this.voteChainEntryHashPayloadSchema;
    }

    public void setVoteChainEntryHashPayloadSchema(final String voteChainEntryHashPayloadSchema) {
        this.voteChainEntryHashPayloadSchema = voteChainEntryHashPayloadSchema;
    }

    public String getHashAlgorithm() {
        return this.hashAlgorithm;
    }

    public void setHashAlgorithm(final String hashAlgorithm) {
        this.hashAlgorithm = hashAlgorithm;
    }

    public String getOperatorPublicKey() {
        return this.operatorPublicKey;
    }

    public void setOperatorPublicKey(final String operatorPublicKey) {
        this.operatorPublicKey = operatorPublicKey;
    }

    public String getImportPublicKey() {
        return this.importPublicKey;
    }

    public void setImportPublicKey(final String importPublicKey) {
        this.importPublicKey = importPublicKey;
    }

    public String getViewingPublicKey() {
        return this.viewingPublicKey;
    }

    public void setViewingPublicKey(final String viewingPublicKey) {
        this.viewingPublicKey = viewingPublicKey;
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

    public Instant getFrozenAt() {
        return this.frozenAt;
    }

    public void setFrozenAt(final Instant frozenAt) {
        this.frozenAt = frozenAt;
    }
}
