package org.sure_vote.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public final class CanonicalJson {
    private static final DateTimeFormatter FORMAT = format();
    private static final String DIGITS = "0123456789abcdef";

    private CanonicalJson() {
    }

    public static String quote(final String value) {
        final var escaped = new StringBuilder(value.length() + 2);
        final var chars = value.toCharArray();
        escaped.append('"');
        for (final var ch : chars) {
            appendEscaped(escaped, ch);
        }
        escaped.append('"');
        return escaped.toString();
    }

    public static String nullableString(final String value) {
        if (value == null) {
            return "null";
        }
        else {
            return quote(value);
        }
    }

    public static String uuid(final UUID value) {
        if (value == null) {
            return "null";
        }
        else {
            final var text = value.toString();
            return quote(text);
        }
    }

    public static String timestamp(final Instant instant) {
        return quote(timestampText(instant));
    }

    public static String timestampText(final Instant instant) {
        final var millis = Instant.ofEpochMilli(instant.toEpochMilli());
        return FORMAT.format(millis);
    }

    public static String bool(final boolean value) {
        if (value) {
            return "true";
        }
        else {
            return "false";
        }
    }

    public static String number(final long value) {
        return Long.toString(value);
    }

    public static String field(final String key, final String encodedValue) {
        return quote(key) + ":" + encodedValue;
    }

    public static String object(final List<String> fields) {
        final var body = String.join(",", fields);
        return "{" + body + "}";
    }

    public static String array(final List<String> elements) {
        final var body = String.join(",", elements);
        return "[" + body + "]";
    }

    public static String strings(final List<String> values) {
        final var encoded = new java.util.ArrayList<String>();
        for (final var value : values) {
            encoded.add(quote(value));
        }
        return array(encoded);
    }

    private static void appendEscaped(final StringBuilder escaped, final char ch) {
        if (ch == '"') {
            escaped.append("\\\"");
        }
        else if (ch == '\\') {
            escaped.append("\\\\");
        }
        else if (ch < 0x20) {
            appendControl(escaped, ch);
        }
        else {
            escaped.append(ch);
        }
    }

    private static void appendControl(final StringBuilder escaped, final char ch) {
        final var high = DIGITS.charAt((ch >> 4) & 0xf);
        final var low = DIGITS.charAt(ch & 0xf);
        escaped.append("\\u00");
        escaped.append(high);
        escaped.append(low);
    }

    private static DateTimeFormatter format() {
        final var pattern = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        return pattern.withZone(ZoneOffset.UTC);
    }
}
