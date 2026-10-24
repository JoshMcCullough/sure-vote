package org.sure_vote.service;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

public final class Signatures {
    private static final String PUBLIC_PREFIX = "302a300506032b6570032100";
    private static final String PRIVATE_PREFIX = "302e020100300506032b657004220420";

    private Signatures() {
    }

    public static SigningKey generate() {
        try {
            final var generator = KeyPairGenerator.getInstance("Ed25519");
            final var pair = generator.generateKeyPair();
            final var publicKey = pair.getPublic();
            final var privateKey = pair.getPrivate();
            final var publicEncoded = publicKey.getEncoded();
            final var privateEncoded = privateKey.getEncoded();
            final var rawPublic = tail(publicEncoded);
            final var rawSeed = tail(privateEncoded);
            return new SigningKey(Digests.hex(rawPublic), rawSeed);
        }
        catch (final GeneralSecurityException failure) {
            throw new IllegalStateException("Ed25519 is required", failure);
        }
    }

    public static String sign(final byte[] seed, final byte[] hash) {
        try {
            final var encoded = wrapPrivate(seed);
            final var spec = new PKCS8EncodedKeySpec(encoded);
            final var factory = KeyFactory.getInstance("Ed25519");
            final var key = factory.generatePrivate(spec);
            final var signer = Signature.getInstance("Ed25519");
            return Digests.hex(signed(signer, key, hash));
        }
        catch (final GeneralSecurityException failure) {
            throw new IllegalStateException("Ed25519 sign failed", failure);
        }
    }

    public static boolean verify(final String publicKeyHex, final byte[] hash, final String signatureHex) {
        try {
            final var publicKey = Digests.unhex(publicKeyHex);
            final var signature = Digests.unhex(signatureHex);
            final var lengths = publicKey.length == 32 && signature.length == 64 && hash.length == 32;
            if (!lengths) {
                return false;
            }
            else {
                final var encoded = wrapPublic(publicKey);
                final var spec = new X509EncodedKeySpec(encoded);
                final var factory = KeyFactory.getInstance("Ed25519");
                final var key = factory.generatePublic(spec);
                final var verifier = Signature.getInstance("Ed25519");
                verifier.initVerify(key);
                verifier.update(hash);
                return verifier.verify(signature);
            }
        }
        catch (final RuntimeException | GeneralSecurityException failure) {
            return false;
        }
    }

    private static byte[] signed(
        final Signature signer,
        final PrivateKey key,
        final byte[] hash
    ) throws GeneralSecurityException {
        signer.initSign(key);
        signer.update(hash);
        return signer.sign();
    }

    private static byte[] wrapPublic(final byte[] raw) {
        final var prefix = Digests.unhex(PUBLIC_PREFIX);
        return concat(prefix, raw);
    }

    private static byte[] wrapPrivate(final byte[] raw) {
        final var prefix = Digests.unhex(PRIVATE_PREFIX);
        return concat(prefix, raw);
    }

    private static byte[] tail(final byte[] encoded) {
        final var start = encoded.length - 32;
        return Arrays.copyOfRange(encoded, start, encoded.length);
    }

    private static byte[] concat(final byte[] left, final byte[] right) {
        final var combined = new byte[left.length + right.length];
        System.arraycopy(left, 0, combined, 0, left.length);
        System.arraycopy(right, 0, combined, left.length, right.length);
        return combined;
    }
}
