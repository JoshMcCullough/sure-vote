package org.sure_vote.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

final class Tokens {
    private static final SecureRandom RANDOM = new SecureRandom();

    private Tokens() {
    }

    static String random() {
        final var bytes = new byte[32];
        final var encoder = Base64.getUrlEncoder();
        RANDOM.nextBytes(bytes);
        return encoder
            .withoutPadding()
            .encodeToString(bytes);
    }

    static String hash(final String token) {
        return Digests.sha256Hex(token);
    }

    static UUID derived(final String purpose, final String value) {
        final var text = purpose + ":" + value;
        final var digest = Digests.sha256(text.getBytes(StandardCharsets.UTF_8));
        final var buffer = ByteBuffer.wrap(digest);
        final var high = buffer.getLong();
        final var low = buffer.getLong();
        final var versioned = (high & 0xffffffffffff0fffL) | 0x0000000000008000L;
        final var variant = (low & 0x3fffffffffffffffL) | 0x8000000000000000L;
        return new UUID(versioned, variant);
    }

    static byte[] bytes(final UUID id) {
        final var buffer = ByteBuffer.allocate(16);
        buffer.putLong(id.getMostSignificantBits());
        buffer.putLong(id.getLeastSignificantBits());
        return buffer.array();
    }

    static UUID uuid(final byte[] bytes) {
        final var buffer = ByteBuffer.wrap(bytes);
        final var high = buffer.getLong();
        final var low = buffer.getLong();
        return new UUID(high, low);
    }
}
