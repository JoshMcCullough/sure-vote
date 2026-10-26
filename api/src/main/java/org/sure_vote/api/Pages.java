package org.sure_vote.api;

import java.nio.file.Path;

public record Pages(Path workspace, Path voter) {
    static final String WORKSPACE_POLICY = "default-src 'none'; script-src 'self'; style-src 'self'; "
        + "img-src 'self' data:; font-src 'self'; connect-src 'self'; manifest-src 'self'; "
        + "base-uri 'none'; form-action 'self'; frame-ancestors 'none'";

    static final String VOTER_POLICY = "default-src 'none'; script-src 'self'; style-src 'self'; "
        + "img-src 'self' data:; font-src 'self'; connect-src 'self'; manifest-src 'self'; "
        + "base-uri 'none'; form-action 'none'; frame-ancestors 'none'";

    public static Pages none() {
        return new Pages(null, null);
    }
}
