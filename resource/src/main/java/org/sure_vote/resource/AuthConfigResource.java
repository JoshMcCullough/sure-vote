package org.sure_vote.resource;

public final class AuthConfigResource {
    private boolean devSignIn;
    private String surface;
    private String lookupUrl;

    public boolean isDevSignIn() {
        return this.devSignIn;
    }

    public void setDevSignIn(final boolean devSignIn) {
        this.devSignIn = devSignIn;
    }

    public String getSurface() {
        return this.surface;
    }

    public void setSurface(final String surface) {
        this.surface = surface;
    }

    public String getLookupUrl() {
        return this.lookupUrl;
    }

    public void setLookupUrl(final String lookupUrl) {
        this.lookupUrl = lookupUrl;
    }
}
