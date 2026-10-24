package org.sure_vote.resource;

import java.util.UUID;

public final class ElectionResource {
    private UUID id;
    private UUID tenantId;
    private String name;
    private String status;
    private String votingStart;
    private String votingEnd;
    private String hash;
    private String signature;
    private boolean demonstration;

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

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getStatus() {
        return this.status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    public String getVotingStart() {
        return this.votingStart;
    }

    public void setVotingStart(final String votingStart) {
        this.votingStart = votingStart;
    }

    public String getVotingEnd() {
        return this.votingEnd;
    }

    public void setVotingEnd(final String votingEnd) {
        this.votingEnd = votingEnd;
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

    public boolean getDemonstration() {
        return this.demonstration;
    }

    public void setDemonstration(final boolean demonstration) {
        this.demonstration = demonstration;
    }
}
