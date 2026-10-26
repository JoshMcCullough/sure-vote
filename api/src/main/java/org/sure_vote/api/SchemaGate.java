package org.sure_vote.api;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.sure_vote.repository.SchemaVersions;

/**
 * Holds a surface that does not migrate until the database has reached the schema its build expects. While the
 * database is behind, or cannot be read, the process has not opened its port, so readiness fails, and it tries again
 * with backoff. It never serves on an older schema. A newer schema is served: that is a rolling deploy in which the
 * workspace has already migrated.
 */
final class SchemaGate {
    static final Duration FIRST_PAUSE = Duration.ofSeconds(1);

    static final Duration LONGEST_PAUSE = Duration.ofSeconds(30);

    /** Sleeps between attempts; tests replace it. */
    interface Pause {
        void pause(Duration length) throws InterruptedException;
    }

    private SchemaGate() {
    }

    static String await(
        final Surface surface,
        final String expected,
        final Supplier<String> applied,
        final Pause pause,
        final Consumer<String> log
    ) {
        var wait = FIRST_PAUSE;
        while (true) {
            final var current = read(applied, log);
            if (current != null && SchemaVersions.compare(current, expected) >= 0) {
                return ready(surface, expected, current, log);
            }
            else {
                behind(surface, expected, current, log);
                paused(pause, wait);
                wait = longer(wait);
            }
        }
    }

    private static String read(final Supplier<String> applied, final Consumer<String> log) {
        try {
            return applied.get();
        }
        catch (final RuntimeException failure) {
            log.accept("SureVote could not read the database schema version (" + failure.getMessage()
                + "). It tries again.");
            return null;
        }
    }

    private static void behind(
        final Surface surface,
        final String expected,
        final String current,
        final Consumer<String> log
    ) {
        if (current != null) {
            log.accept("SureVote " + surface.wire() + " surface: the database schema is at version "
                + shown(current) + " and this build needs " + expected
                + ". Waiting for the workspace surface to migrate; not serving until it has.");
        }
    }

    private static String ready(
        final Surface surface,
        final String expected,
        final String current,
        final Consumer<String> log
    ) {
        if (SchemaVersions.compare(current, expected) > 0) {
            log.accept("SureVote " + surface.wire() + " surface: the database schema is at version " + current
                + ", newer than this build's " + expected + ". Serving, as in a rolling deploy.");
            return current;
        }
        else {
            return current;
        }
    }

    private static String shown(final String version) {
        if (version.isEmpty()) {
            return "none";
        }
        else {
            return version;
        }
    }

    private static void paused(final Pause pause, final Duration length) {
        try {
            pause.pause(length);
        }
        catch (final InterruptedException interrupted) {
            final var thread = Thread.currentThread();
            thread.interrupt();
            throw new IllegalStateException("Interrupted while waiting for the database schema", interrupted);
        }
    }

    private static Duration longer(final Duration wait) {
        final var doubled = wait.multipliedBy(2);
        if (doubled.compareTo(LONGEST_PAUSE) > 0) {
            return LONGEST_PAUSE;
        }
        else {
            return doubled;
        }
    }
}
