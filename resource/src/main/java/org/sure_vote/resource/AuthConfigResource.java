package org.sure_vote.resource;

public final class AuthConfigResource {
    private boolean devSignIn;

    public boolean isDevSignIn() {
        return this.devSignIn;
    }

    public void setDevSignIn(final boolean devSignIn) {
        this.devSignIn = devSignIn;
    }
}
