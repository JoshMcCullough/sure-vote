package org.sure_vote.resource;

import java.util.UUID;

public final class AssignmentResource {
    private UUID id;

    private UUID tenantId;

    private UUID electionId;

    private UUID votingPrecinctId;

    private String assignmentStatus;

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

    public UUID getElectionId() {
        return this.electionId;
    }

    public void setElectionId(final UUID electionId) {
        this.electionId = electionId;
    }

    public UUID getVotingPrecinctId() {
        return this.votingPrecinctId;
    }

    public void setVotingPrecinctId(final UUID votingPrecinctId) {
        this.votingPrecinctId = votingPrecinctId;
    }

    public String getAssignmentStatus() {
        return this.assignmentStatus;
    }

    public void setAssignmentStatus(final String assignmentStatus) {
        this.assignmentStatus = assignmentStatus;
    }
}
