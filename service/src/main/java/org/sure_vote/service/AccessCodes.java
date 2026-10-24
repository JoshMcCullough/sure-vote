package org.sure_vote.service;

import java.security.SecureRandom;
import java.util.Base64;

public final class AccessCodes {
    private AccessCodes() {
    }

    public static byte[] generate() {
        final var bytes = new byte[32];
        final var random = new SecureRandom();
        random.nextBytes(bytes);
        return bytes;
    }

    public static String encode(final byte[] raw) {
        final var encoder = Base64.getUrlEncoder();
        final var unpadded = encoder.withoutPadding();
        return unpadded.encodeToString(raw);
    }

    public static byte[] decode(final String text) {
        final var decoder = Base64.getUrlDecoder();
        return decoder.decode(text);
    }

    public static String inPersonHash(final byte[] raw) {
        return Digests.hex(Digests.sha256(raw));
    }
}
