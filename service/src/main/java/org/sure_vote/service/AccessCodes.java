package org.sure_vote.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

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

    public static String mailHash(final String passphrase, final byte[] salt) {
        final var normalized = Passphrases.normalize(passphrase);
        final var password = normalized.getBytes(StandardCharsets.UTF_8);
        final var output = new byte[32];
        argon2(password, salt, output);
        return Digests.hex(output);
    }

    private static void argon2(final byte[] password, final byte[] salt, final byte[] output) {
        final var builder = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id);
        final var versioned = builder.withVersion(Argon2Parameters.ARGON2_VERSION_13);
        final var iterated = versioned.withIterations(3);
        final var sized = iterated.withMemoryAsKB(65536);
        final var parallel = sized.withParallelism(1);
        final var salted = parallel.withSalt(salt);
        final var parameters = salted.build();
        final var generator = new Argon2BytesGenerator();
        generator.init(parameters);
        generator.generateBytes(password, output, 0, output.length);
    }
}
