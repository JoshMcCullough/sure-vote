package org.sure_vote.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

import org.bouncycastle.crypto.digests.Blake2bDigest;
import org.bouncycastle.crypto.engines.XSalsa20Engine;
import org.bouncycastle.crypto.macs.Poly1305;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;

/**
 * Libsodium {@code crypto_box_seal}: ephemeral X25519, then XSalsa20-Poly1305.
 * The nonce is BLAKE2b of the ephemeral public key followed by the recipient public key.
 */
final class ViewingBoxes {
    private ViewingBoxes() {
    }

    static byte[] seal(final byte[] message, final byte[] recipientPublic) {
        final var ephemeral = randomSeed();
        try {
            return sealWith(message, recipientPublic, ephemeral);
        }
        finally {
            wipe(ephemeral);
        }
    }

    static byte[] sealWith(final byte[] message, final byte[] recipientPublic, final byte[] ephemeralSeed) {
        final var ephemeralPublic = publicKey(ephemeralSeed);
        final var shared = shared(ephemeralSeed, recipientPublic);
        final var key = boxKey(shared);
        final var nonce = nonce(ephemeralPublic, recipientPublic);
        final var boxed = secretBox(key, nonce, message);
        final var sealed = join(ephemeralPublic, boxed);
        wipe(shared);
        wipe(key);
        wipe(nonce);
        return sealed;
    }

    static byte[] open(final byte[] sealed, final byte[] recipientPublic, final byte[] recipientSeed) {
        if (sealed == null || recipientPublic == null || recipientSeed == null || sealed.length < 48) {
            throw new ConstraintException("viewing passphrase cannot be opened");
        }
        else {
            return opened(sealed, recipientPublic, recipientSeed);
        }
    }

    static byte[] publicKey(final byte[] seed) {
        final var scalar = new X25519PrivateKeyParameters(seed, 0);
        final var point = scalar.generatePublicKey();
        return point.getEncoded();
    }

    private static byte[] opened(final byte[] sealed, final byte[] recipientPublic, final byte[] recipientSeed) {
        final var ephemeralPublic = Arrays.copyOfRange(sealed, 0, 32);
        final var boxed = Arrays.copyOfRange(sealed, 32, sealed.length);
        final var shared = shared(recipientSeed, ephemeralPublic);
        final var key = boxKey(shared);
        final var nonce = nonce(ephemeralPublic, recipientPublic);
        final var plain = secretOpen(key, nonce, boxed);
        wipe(shared);
        wipe(key);
        wipe(nonce);
        if (plain == null) {
            throw new ConstraintException("viewing passphrase cannot be opened");
        }
        else {
            return plain;
        }
    }

    private static byte[] randomSeed() {
        final var seed = new byte[32];
        final var random = new SecureRandom();
        random.nextBytes(seed);
        return seed;
    }

    static byte[] shared(final byte[] seed, final byte[] peerPublic) {
        try {
            return multiply(seed, peerPublic);
        }
        catch (final RuntimeException failure) {
            throw new ConstraintException("viewing passphrase cannot be opened");
        }
    }

    private static byte[] multiply(final byte[] seed, final byte[] peerPublic) {
        final var scalar = new X25519PrivateKeyParameters(seed, 0);
        final var point = new X25519PublicKeyParameters(peerPublic, 0);
        final var secret = new byte[32];
        scalar.generateSecret(point, secret, 0);
        return secret;
    }

    static byte[] boxKey(final byte[] sharedSecret) {
        final var zero = new byte[16];
        return hsalsa20(zero, sharedSecret);
    }

    static byte[] nonce(final byte[] ephemeralPublic, final byte[] recipientPublic) {
        final var digest = new Blake2bDigest(192);
        final var out = new byte[24];
        digest.update(ephemeralPublic, 0, ephemeralPublic.length);
        digest.update(recipientPublic, 0, recipientPublic.length);
        digest.doFinal(out, 0);
        return out;
    }

    private static byte[] secretBox(final byte[] key, final byte[] nonce, final byte[] message) {
        final var engine = new XSalsa20Engine();
        final var parameters = new ParametersWithIV(new KeyParameter(key), nonce);
        final var input = new byte[32 + message.length];
        final var output = new byte[input.length];
        return boxed(engine, parameters, message, input, output);
    }

    private static byte[] boxed(
        final XSalsa20Engine engine,
        final ParametersWithIV parameters,
        final byte[] message,
        final byte[] input,
        final byte[] output
    ) {
        System.arraycopy(message, 0, input, 32, message.length);
        engine.init(true, parameters);
        engine.processBytes(input, 0, input.length, output, 0);
        return tagged(input, output);
    }

    private static byte[] tagged(final byte[] input, final byte[] output) {
        final var polyKey = Arrays.copyOf(output, 32);
        final var cipher = Arrays.copyOfRange(output, 32, output.length);
        final var tag = mac(polyKey, cipher);
        wipe(input);
        wipe(output);
        wipe(polyKey);
        return join(tag, cipher);
    }

    private static byte[] secretOpen(final byte[] key, final byte[] nonce, final byte[] boxed) {
        if (boxed.length < 16) {
            return null;
        }
        else {
            return unboxed(key, nonce, boxed);
        }
    }

    private static byte[] unboxed(final byte[] key, final byte[] nonce, final byte[] boxed) {
        final var tag = Arrays.copyOfRange(boxed, 0, 16);
        final var cipher = Arrays.copyOfRange(boxed, 16, boxed.length);
        final var engine = new XSalsa20Engine();
        final var parameters = new ParametersWithIV(new KeyParameter(key), nonce);
        final var polyKey = new byte[32];
        final var plain = new byte[cipher.length];
        return opened(engine, parameters, tag, cipher, polyKey, plain);
    }

    private static byte[] opened(
        final XSalsa20Engine engine,
        final ParametersWithIV parameters,
        final byte[] tag,
        final byte[] cipher,
        final byte[] polyKey,
        final byte[] plain
    ) {
        engine.init(true, parameters);
        engine.processBytes(polyKey, 0, polyKey.length, polyKey, 0);
        return checked(engine, tag, cipher, polyKey, plain);
    }

    private static byte[] checked(
        final XSalsa20Engine engine,
        final byte[] tag,
        final byte[] cipher,
        final byte[] polyKey,
        final byte[] plain
    ) {
        final var expected = mac(polyKey, cipher);
        engine.processBytes(cipher, 0, cipher.length, plain, 0);
        wipe(polyKey);
        if (same(tag, expected)) {
            return plain;
        }
        else {
            wipe(plain);
            return null;
        }
    }

    private static byte[] mac(final byte[] key, final byte[] body) {
        final var poly = new Poly1305();
        final var parameter = new KeyParameter(key);
        final var out = new byte[16];
        poly.init(parameter);
        poly.update(body, 0, body.length);
        poly.doFinal(out, 0);
        return out;
    }

    private static byte[] hsalsa20(final byte[] nonce, final byte[] key) {
        final var state = new int[16];
        final var mixed = new int[16];
        load(state, key, nonce);
        rounds(state, mixed);
        return extract(mixed);
    }

    private static void load(final int[] state, final byte[] key, final byte[] nonce) {
        final var constant = "expand 32-byte k".getBytes(StandardCharsets.US_ASCII);
        for (var index = 0; index < 4; index = index + 1) {
            state[5 * index] = little(constant, 4 * index);
            state[1 + index] = little(key, 4 * index);
            state[6 + index] = little(nonce, 4 * index);
            state[11 + index] = little(key, 16 + 4 * index);
        }
    }

    private static void rounds(final int[] state, final int[] mixed) {
        copy(mixed, state);
        for (var round = 0; round < 20; round = round + 1) {
            permute(mixed);
        }
    }

    private static void permute(final int[] state) {
        final var next = new int[16];
        for (var column = 0; column < 4; column = column + 1) {
            quarter(state, next, column);
        }
        copy(state, next);
    }

    private static void quarter(final int[] state, final int[] next, final int column) {
        final var words = new int[4];
        for (var part = 0; part < 4; part = part + 1) {
            words[part] = state[(5 * column + 4 * part) % 16];
        }
        words[1] = words[1] ^ rotl(words[0] + words[3], 7);
        words[2] = words[2] ^ rotl(words[1] + words[0], 9);
        words[3] = words[3] ^ rotl(words[2] + words[1], 13);
        words[0] = words[0] ^ rotl(words[3] + words[2], 18);
        for (var part = 0; part < 4; part = part + 1) {
            next[4 * column + (column + part) % 4] = words[part];
        }
    }

    private static byte[] extract(final int[] state) {
        final var out = new byte[32];
        store(out, 0, state[0]);
        store(out, 4, state[5]);
        store(out, 8, state[10]);
        store(out, 12, state[15]);
        store(out, 16, state[6]);
        store(out, 20, state[7]);
        store(out, 24, state[8]);
        store(out, 28, state[9]);
        return out;
    }

    private static int little(final byte[] bytes, final int offset) {
        final var b0 = bytes[offset] & 0xff;
        final var b1 = bytes[offset + 1] & 0xff;
        final var b2 = bytes[offset + 2] & 0xff;
        final var b3 = bytes[offset + 3] & 0xff;
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private static void store(final byte[] out, final int offset, final int value) {
        out[offset] = (byte) value;
        out[offset + 1] = (byte) (value >>> 8);
        out[offset + 2] = (byte) (value >>> 16);
        out[offset + 3] = (byte) (value >>> 24);
    }

    private static int rotl(final int value, final int bits) {
        return (value << bits) | (value >>> (32 - bits));
    }

    private static void copy(final int[] into, final int[] from) {
        for (var index = 0; index < from.length; index = index + 1) {
            into[index] = from[index];
        }
    }

    private static byte[] join(final byte[] left, final byte[] right) {
        final var joined = new byte[left.length + right.length];
        System.arraycopy(left, 0, joined, 0, left.length);
        System.arraycopy(right, 0, joined, left.length, right.length);
        return joined;
    }

    private static boolean same(final byte[] left, final byte[] right) {
        if (left.length != right.length) {
            return false;
        }
        else {
            return sameBytes(left, right);
        }
    }

    private static boolean sameBytes(final byte[] left, final byte[] right) {
        var diff = 0;
        for (var index = 0; index < left.length; index = index + 1) {
            diff = diff | (left[index] ^ right[index]);
        }
        return diff == 0;
    }

    static void wipe(final byte[] bytes) {
        if (bytes != null) {
            Arrays.fill(bytes, (byte) 0);
        }
    }
}
