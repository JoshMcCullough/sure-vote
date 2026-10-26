package org.sure_vote.repository;

/**
 * Orders Flyway versions such as {@code 9}, {@code 18}, or {@code 2.1} part by part as numbers. The empty string, a
 * database with no migration yet, comes before every version.
 */
public final class SchemaVersions {
    private SchemaVersions() {
    }

    public static int compare(final String left, final String right) {
        final var leftParts = parts(left);
        final var rightParts = parts(right);
        final var length = Math.max(leftParts.length, rightParts.length);
        var order = 0;
        for (var index = 0; index < length && order == 0; index++) {
            order = Long.compare(part(leftParts, index), part(rightParts, index));
        }
        return order;
    }

    private static String[] parts(final String version) {
        if (version == null || version.isBlank()) {
            return new String[0];
        }
        else {
            return version.split("[._]");
        }
    }

    private static long part(final String[] parts, final int index) {
        if (index < parts.length) {
            return Long.parseLong(parts[index]);
        }
        else {
            return -1L;
        }
    }
}
