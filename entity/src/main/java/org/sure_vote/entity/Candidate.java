package org.sure_vote.entity;

import java.util.UUID;

public final class Candidate extends TenantEntity {
    private UUID personId;
    private CandidateStatus status;
    private UUID electedOfficialId;
    private String partyAffiliation;
    private String biography;
    private UUID photoId;

    public UUID getPersonId() {
        return this.personId;
    }

    public void setPersonId(final UUID personId) {
        this.personId = personId;
    }

    public CandidateStatus getStatus() {
        return this.status;
    }

    public void setStatus(final CandidateStatus status) {
        this.status = status;
    }

    public UUID getElectedOfficialId() {
        return this.electedOfficialId;
    }

    public void setElectedOfficialId(final UUID electedOfficialId) {
        this.electedOfficialId = electedOfficialId;
    }

    public String getPartyAffiliation() {
        return this.partyAffiliation;
    }

    public void setPartyAffiliation(final String partyAffiliation) {
        this.partyAffiliation = partyAffiliation;
    }

    public String getBiography() {
        return this.biography;
    }

    public void setBiography(final String biography) {
        this.biography = biography;
    }

    public UUID getPhotoId() {
        return this.photoId;
    }

    public void setPhotoId(final UUID photoId) {
        this.photoId = photoId;
    }
}
