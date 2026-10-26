package org.sure_vote.api;

import java.util.Map;

/**
 * The surface rules a process checks before it opens a database or a port. Each failure stops start-up.
 */
final class Startup {
    private Startup() {
    }

    static Surface checked(final Surface surface, final Map<String, String> env) {
        require(surface, env);
        return surface;
    }

    static void require(final Surface surface, final Map<String, String> env) {
        final var database = set(env, "SUREVOTE_JDBC_URL");
        final var devSignIn = "true".equals(value(env, "SUREVOTE_DEV_SIGN_IN"));
        if (devSignIn && (surface == Surface.PUBLIC || surface == Surface.INGEST)) {
            throw new IllegalStateException("Dev sign-in cannot run on the " + surface.wire() + " surface");
        }
        else if (surface == Surface.PUBLIC && database) {
            requirePublicRoles(env);
        }
        else if (surface == Surface.DEMO && !set(env, "SUREVOTE_DEMO_TENANT_ID")) {
            throw new IllegalStateException("The demo surface needs SUREVOTE_DEMO_TENANT_ID");
        }
    }

    private static void requirePublicRoles(final Map<String, String> env) {
        if (set(env, "SUREVOTE_JDBC_APP_USER")) {
            throw new IllegalStateException(
                "The public surface must not use the writer role. Unset SUREVOTE_JDBC_APP_USER and set "
                    + "SUREVOTE_JDBC_DISPUTE_USER"
            );
        }
        else if (!set(env, "SUREVOTE_JDBC_PUBLIC_USER")) {
            throw new IllegalStateException("The public surface needs SUREVOTE_JDBC_PUBLIC_USER");
        }
        else if (!set(env, "SUREVOTE_JDBC_DISPUTE_USER")) {
            throw new IllegalStateException("The public surface needs SUREVOTE_JDBC_DISPUTE_USER");
        }
    }

    private static boolean set(final Map<String, String> env, final String name) {
        final var text = value(env, name);
        return !text.isBlank();
    }

    static String value(final Map<String, String> env, final String name) {
        final var value = env.get(name);
        if (value == null) {
            return "";
        }
        else {
            return value;
        }
    }
}
