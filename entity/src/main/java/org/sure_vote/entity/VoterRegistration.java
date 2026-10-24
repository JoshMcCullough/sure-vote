package org.sure_vote.entity;

import java.time.LocalDate;
import java.util.UUID;

public final class VoterRegistration extends TenantEntity {
    private UUID personId;
    private LocalDate date;
    private RegistrationStatus status;
    private UUID addressId;
    private String partyAffiliation;
    private UUID votingPrecinctId;
    private LocalDate assignmentDate;
    private AssignmentStatus assignmentStatus;

    public UUID getPersonId() {
        return this.personId;
    }

    public void setPersonId(final UUID personId) {
        this.personId = personId;
    }

    public LocalDate getDate() {
        return this.date;
    }

    public void setDate(final LocalDate date) {
        this.date = date;
    }

    public RegistrationStatus getStatus() {
        return this.status;
    }

    public void setStatus(final RegistrationStatus status) {
        this.status = status;
    }

    public UUID getAddressId() {
        return this.addressId;
    }

    public void setAddressId(final UUID addressId) {
        this.addressId = addressId;
    }

    public String getPartyAffiliation() {
        return this.partyAffiliation;
    }

    public void setPartyAffiliation(final String partyAffiliation) {
        this.partyAffiliation = partyAffiliation;
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
}
