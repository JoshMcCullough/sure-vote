package org.sure_vote.entity;

import java.time.LocalDate;
import java.util.UUID;

public final class ElectionVotingPrecinctAssignment extends TenantEntity {
    private UUID electionId;
    private UUID votingPrecinctId;
    private LocalDate assignmentDate;
    private AssignmentStatus assignmentStatus;
    private int numberOfVoters;

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

    public LocalDate getAssignmentDate() {
        return this.assignmentDate;
    }

    public void setAssignmentDate(final LocalDate assignmentDate) {
        this.assignmentDate = assignmentDate;
    }

    public AssignmentStatus getAssignmentStatus() {
        return this.assignmentStatus;
    }

    public void setAssignmentStatus(final AssignmentStatus assignmentStatus) {
        this.assignmentStatus = assignmentStatus;
    }

    public int getNumberOfVoters() {
        return this.numberOfVoters;
    }

    public void setNumberOfVoters(final int numberOfVoters) {
        this.numberOfVoters = numberOfVoters;
    }
}
