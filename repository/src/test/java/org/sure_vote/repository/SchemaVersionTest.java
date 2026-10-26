package org.sure_vote.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaVersionTest {
    private static final Pattern MIGRATION = Pattern.compile("V([0-9._]+)__.+\\.sql");

    @Test
    void theBuildVersionIsTheNewestMigration() throws IOException {
        final var folder = Path.of("src/main/resources/db/migration");
        var newest = "";
        try (var files = Files.list(folder)) {
            for (final var file : (Iterable<Path>) files::iterator) {
                final var name = file.getFileName();
                final var match = MIGRATION.matcher(name.toString());
                if (match.matches() && SchemaVersions.compare(match.group(1), newest) > 0) {
                    newest = match.group(1);
                }
            }
        }
        assertEquals(newest, JdbcSureVoteRepository.SCHEMA_VERSION);
    }

    @Test
    void versionsCompareAsNumbersPartByPart() {
        assertTrue(SchemaVersions.compare("18", "9") > 0);
        assertTrue(SchemaVersions.compare("2.1", "2") > 0);
        assertTrue(SchemaVersions.compare("17", "18") < 0);
        assertTrue(SchemaVersions.compare("", "1") < 0);
        assertEquals(0, SchemaVersions.compare("18", "18"));
    }
}
