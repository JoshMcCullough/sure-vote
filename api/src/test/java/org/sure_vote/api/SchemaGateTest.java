package org.sure_vote.api;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaGateTest {
    private final List<Duration> pauses = new ArrayList<>();

    private final List<String> log = new ArrayList<>();

    @Test
    void aCurrentSchemaPassesAtOnce() {
        final var applied = SchemaGate.await(Surface.PUBLIC, "18", () -> "18", this.pauses::add, this.log::add);
        assertEquals("18", applied);
        assertEquals(List.of(), this.pauses);
    }

    @Test
    void aSchemaBehindWaitsWithBackoffUntilTheWorkspaceHasMigrated() {
        final var listed = List.of("", "16", "17", "17", "17", "17", "17", "18");
        final var versions = listed.iterator();
        final var applied = SchemaGate.await(Surface.DEMO, "18", versions::next, this.pauses::add, this.log::add);
        final var seconds = this.pauses
            .stream()
            .map(Duration::toSeconds)
            .toList();
        assertEquals("18", applied);
        assertEquals(List.of(1L, 2L, 4L, 8L, 16L, 30L, 30L), seconds);
        assertTrue(this.log
            .getFirst()
            .contains("at version none and this build needs 18"));
        assertTrue(this.log
            .get(1)
            .contains("Waiting for the workspace surface to migrate; not serving"));
    }

    @Test
    void aSchemaThatStaysBehindIsNeverServed() {
        final var reads = new int[] {0};
        final var failure = assertThrows(IllegalStateException.class, () -> SchemaGate.await(
            Surface.INGEST,
            "18",
            () -> {
                reads[0]++;
                return "17";
            },
            this::stopAfterFive,
            this.log::add
        ));
        assertEquals("Interrupted while waiting for the database schema", failure.getMessage());
        assertEquals(5, reads[0]);
        assertTrue(Thread.interrupted());
    }

    @Test
    void anUnreadableSchemaIsRetried() {
        final var failures = new int[] {2};
        final var applied = SchemaGate.await(Surface.PUBLIC, "18", () -> {
            if (failures[0]-- > 0) {
                throw new IllegalStateException("permission denied for table flyway_schema_history");
            }
            else {
                return "18";
            }
        }, this.pauses::add, this.log::add);
        assertEquals("18", applied);
        assertEquals(2, this.pauses.size());
        assertTrue(this.log
            .getFirst()
            .contains("permission denied for table flyway_schema_history"));
    }

    private void stopAfterFive(final Duration length) throws InterruptedException {
        this.pauses.add(length);
        if (this.pauses.size() == 5) {
            throw new InterruptedException("stopped");
        }
    }

    @Test
    void aNewerSchemaIsServedAsARollingDeploy() {
        final var applied = SchemaGate.await(Surface.DEMO, "18", () -> "19", this.pauses::add, this.log::add);
        assertEquals("19", applied);
        assertEquals(List.of(), this.pauses);
        assertTrue(this.log
            .getFirst()
            .contains("newer than this build's 18"));
    }
}
