package org.sure_vote.resource;

public final class EnrollmentResource {
    private OfficialResource official;
    private String enrollmentToken;

    public OfficialResource getOfficial() {
        return this.official;
    }

    public void setOfficial(final OfficialResource official) {
        this.official = official;
    }

    public String getEnrollmentToken() {
        return this.enrollmentToken;
    }

    public void setEnrollmentToken(final String enrollmentToken) {
        this.enrollmentToken = enrollmentToken;
    }
}
