package org.sure_vote.api;

import java.util.List;
import java.util.Map;

/**
 * The surface rules a process checks before it opens a database or a port. Each failure stops start-up.
 */
final class Startup {
    /** The owner credential and the settings only a migrating process uses. Flyway also reads {@code FLYWAY_*}. */
    static final List<String> MIGRATION_SETTINGS = List.of(
        "SUREVOTE_JDBC_USER",
        "SUREVOTE_JDBC_PASSWORD",
        "SUREVOTE_GRANT_PUBLIC_ROLE",
        "SUREVOTE_GRANT_DISPUTE_ROLE"
    );

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
        else if (!surface.migrates()) {
            requireNoMigration(surface, env);
            requireRoles(surface, env, database);
        }
        else if (surface == Surface.WORKSPACE && database && !set(env, "SUREVOTE_JDBC_USER")) {
            throw new IllegalStateException(
                "The workspace surface runs the migrations and needs the owner credential in SUREVOTE_JDBC_USER"
            );
        }
    }

    private static void requireNoMigration(final Surface surface, final Map<String, String> env) {
        final var configured = migrationSetting(env);
        if (!configured.isEmpty()) {
            throw new IllegalStateException(
                "The " + surface.wire() + " surface does not migrate. Unset " + configured
                    + "; only the workspace surface holds the owner credential and runs migrations"
            );
        }
    }

    private static String migrationSetting(final Map<String, String> env) {
        final var named = MIGRATION_SETTINGS
            .stream()
            .filter(name -> set(env, name))
            .findFirst();
        final var flyway = env
            .keySet()
            .stream()
            .filter(name -> name.startsWith("FLYWAY_") && set(env, name))
            .sorted()
            .findFirst();
        return named
            .or(() -> flyway)
            .orElse("");
    }

    private static void requireRoles(final Surface surface, final Map<String, String> env, final boolean database) {
        if (surface == Surface.DEMO && !set(env, "SUREVOTE_DEMO_TENANT_ID")) {
            throw new IllegalStateException("The demo surface needs SUREVOTE_DEMO_TENANT_ID");
        }
        else if (surface == Surface.PUBLIC && database) {
            requirePublicRoles(env);
        }
        else if (database && !set(env, "SUREVOTE_JDBC_APP_USER")) {
            throw new IllegalStateException("The " + surface.wire() + " surface needs SUREVOTE_JDBC_APP_USER");
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
