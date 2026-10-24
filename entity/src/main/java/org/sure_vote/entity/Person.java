package org.sure_vote.entity;

import java.time.LocalDate;
import java.util.UUID;

public final class Person extends TenantEntity {
    private String firstName;
    private String middleName;
    private String lastName;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String viewingPassphrase;
    private LocalDate dateOfBirth;
    private UUID addressId;
    private UUID citizenshipId;
    private String ssn;
    private String voterId;

    public String getFirstName() {
        return this.firstName;
    }

    public void setFirstName(final String firstName) {
        this.firstName = firstName;
    }

    public String getMiddleName() {
        return this.middleName;
    }

    public void setMiddleName(final String middleName) {
        this.middleName = middleName;
    }

    public String getLastName() {
        return this.lastName;
    }

    public void setLastName(final String lastName) {
        this.lastName = lastName;
    }

    public String getFullName() {
        return this.fullName;
    }

    public void setFullName(final String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return this.email;
    }

    public void setEmail(final String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return this.phoneNumber;
    }

    public void setPhoneNumber(final String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getViewingPassphrase() {
        return this.viewingPassphrase;
    }

    public void setViewingPassphrase(final String viewingPassphrase) {
        this.viewingPassphrase = viewingPassphrase;
    }

    public LocalDate getDateOfBirth() {
        return this.dateOfBirth;
    }

    public void setDateOfBirth(final LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public UUID getAddressId() {
        return this.addressId;
    }

    public void setAddressId(final UUID addressId) {
        this.addressId = addressId;
    }

    public UUID getCitizenshipId() {
        return this.citizenshipId;
    }

    public void setCitizenshipId(final UUID citizenshipId) {
        this.citizenshipId = citizenshipId;
    }

    public String getSsn() {
        return this.ssn;
    }

    public void setSsn(final String ssn) {
        this.ssn = ssn;
    }

    public String getVoterId() {
        return this.voterId;
    }

    public void setVoterId(final String voterId) {
        this.voterId = voterId;
    }
}
