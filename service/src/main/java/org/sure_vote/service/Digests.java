package org.sure_vote.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Digests {
    private Digests() {
    }

    public static byte[] sha256(final byte[] input) {
        try {
            final var digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        }
        catch (final NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is required", failure);
        }
    }

    public static String sha256Hex(final String text) {
        final var bytes = text.getBytes(StandardCharsets.UTF_8);
        return hex(sha256(bytes));
    }

    public static String hex(final byte[] bytes) {
        final var text = new StringBuilder(bytes.length * 2);
        for (final var value : bytes) {
            final var unsigned = value & 0xff;
            final var high = unsigned >>> 4;
            final var low = unsigned & 0x0f;
            text.append(Character.forDigit(high, 16));
            text.append(Character.forDigit(low, 16));
        }
        return text.toString();
    }

    public static byte[] unhex(final String text) {
        if (text == null || text.length() % 2 != 0 || !isLowerHex(text)) {
            throw new IllegalArgumentException("lowercase hex is required");
        }
        else {
            final var bytes = new byte[text.length() / 2];
            fill(text, bytes);
            return bytes;
        }
    }

    public static boolean isLowerHex(final String text, final int chars) {
        if (text == null || text.length() != chars) {
            return false;
        }
        else {
            return isLowerHex(text);
        }
    }

    private static boolean isLowerHex(final String text) {
        final var chars = text.toCharArray();
        for (final var ch : chars) {
            if (!isHexDigit(ch)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHexDigit(final char ch) {
        if (ch >= '0' && ch <= '9') {
            return true;
        }
        else {
            return ch >= 'a' && ch <= 'f';
        }
    }

    private static void fill(final String text, final byte[] bytes) {
        for (var index = 0; index < bytes.length; index = index + 1) {
            final var high = Character.digit(text.charAt(index * 2), 16);
            final var low = Character.digit(text.charAt(index * 2 + 1), 16);
            bytes[index] = (byte) ((high << 4) + low);
        }
    }
}
