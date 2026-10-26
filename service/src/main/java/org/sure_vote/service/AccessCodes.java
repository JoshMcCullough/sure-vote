package org.sure_vote.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/**
 * Voter access hashes carry their algorithm: {@code sha256:<hex>} for an in-person code, and
 * {@code argon2id-v13:m=65536,t=3,p=1:<hex>} for a mail-in passphrase, where the middle part is the Argon2id
 * memory in KiB, iterations and lanes. The hash sits inside the device-signed ballot payload, so its identifier and
 * parameters are covered by the device signature. Only these exact forms are accepted; other parameters are refused.
 */
public final class AccessCodes {
    public static final String MAIL_PARAMETERS = "m=" + Argon2.MEMORY_KIB + ",t=" + Argon2.ITERATIONS + ",p="
        + Argon2.LANES;
    public static final String MAIL_ACCESS_HASH = Algorithms.ARGON2ID_V13 + ":" + MAIL_PARAMETERS;
    private static final String IN_PERSON_PREFIX = Algorithms.SHA256 + ":";
    private static final String MAIL_PREFIX = MAIL_ACCESS_HASH + ":";

    private AccessCodes() {
    }

    /**
     * The Argon2id parameters for mail-in access hashes.
     */
    private static final class Argon2 {
        static final int MEMORY_KIB = 65536;
        static final int ITERATIONS = 3;
        static final int LANES = 1;
        static final int OUTPUT_BYTES = 32;

        private Argon2() {
        }
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
        return IN_PERSON_PREFIX + Digests.hex(Digests.sha256(raw));
    }

    /**
     * True when {@code hash} is an accepted access hash of the form a ballot of {@code mail} kind uses.
     */
    public static boolean isAccessHash(final String hash, final boolean mail) {
        final var prefix = mail ? MAIL_PREFIX : IN_PERSON_PREFIX;
        if (hash == null || !hash.startsWith(prefix)) {
            return false;
        }
        else {
            return Digests.isLowerHex(hash.substring(prefix.length()), 64);
        }
    }

    public static String mailHash(final String passphrase, final byte[] salt) {
        final var normalized = Passphrases.normalize(passphrase);
        final var password = normalized.getBytes(StandardCharsets.UTF_8);
        final var output = new byte[Argon2.OUTPUT_BYTES];
        argon2(password, salt, output);
        return MAIL_PREFIX + Digests.hex(output);
    }

    private static void argon2(final byte[] password, final byte[] salt, final byte[] output) {
        final var builder = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id);
        final var versioned = builder.withVersion(Argon2Parameters.ARGON2_VERSION_13);
        final var iterated = versioned.withIterations(Argon2.ITERATIONS);
        final var sized = iterated.withMemoryAsKB(Argon2.MEMORY_KIB);
        final var parallel = sized.withParallelism(Argon2.LANES);
        final var salted = parallel.withSalt(salt);
        final var parameters = salted.build();
        final var generator = new Argon2BytesGenerator();
        generator.init(parameters);
        generator.generateBytes(password, output, 0, output.length);
    }
}
