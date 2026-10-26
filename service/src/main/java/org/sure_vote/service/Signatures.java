package org.sure_vote.service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;

/**
 * {@code ecdsa-p256-sha256}: ECDSA over NIST P-256 with SHA-256. The caller passes the 32-byte SHA-256 hash of what
 * it signs; the signed message is the statement {@code {"alg":"ecdsa-p256-sha256","hash":"<hash hex>"}} in UTF-8,
 * so the identifier is inside the signed bytes and a signature cannot be replayed under another algorithm.
 * Public keys are {@code ecdsa-p256-sha256:} then the uncompressed SEC1 point in hex; signatures are
 * {@code ecdsa-p256-sha256:} then IEEE P1363 {@code r || s} (64 bytes) with low S, in hex. A key and a signature
 * must carry the same identifier, and verification refuses any other identifier.
 */
public final class Signatures {
    public static final String ID = Algorithms.ECDSA_P256_SHA256;
    private static final String ALGORITHM = "SHA256withECDSAinP1363Format";
    private static final int SIGNATURE_BYTES = 64;
    private static final int HASH_BYTES = 32;

    private Signatures() {
    }

    public static SigningKey generate() {
        final var pair = pair();
        final var publicKey = (ECPublicKey) pair.getPublic();
        final var privateKey = (ECPrivateKey) pair.getPrivate();
        final var encoded = P256.encode(publicKey.getW());
        final var scalar = P256.scalarBytes(privateKey.getS());
        return new SigningKey(Algorithms.typed(ID, encoded), scalar);
    }

    private static KeyPair pair() {
        try {
            final var generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        }
        catch (final GeneralSecurityException failure) {
            throw new IllegalStateException("P-256 is required", failure);
        }
    }

    public static String publicKey(final byte[] privateScalar) {
        return Algorithms.typed(ID, P256.publicKey(privateScalar));
    }

    /**
     * True for {@code ecdsa-p256-sha256:04...}, an uncompressed point on the curve.
     */
    public static boolean isPublicKey(final String publicKey) {
        return ID.equals(Algorithms.id(publicKey)) && P256.isPoint(Algorithms.body(publicKey));
    }

    /**
     * True for {@code ecdsa-p256-sha256:} then 128 lowercase hex characters. Low S is checked when verifying.
     */
    public static boolean isSignature(final String signature) {
        final var hex = Algorithms.body(signature);
        return ID.equals(Algorithms.id(signature)) && Digests.isLowerHex(hex, 2 * SIGNATURE_BYTES);
    }

    /**
     * The UTF-8 statement ECDSA-SHA256 signs: the identifier and the caller's hash, as canonical JSON.
     */
    public static byte[] statement(final byte[] hash) {
        final var json = "{\"alg\":\"" + ID + "\",\"hash\":\"" + Digests.hex(hash) + "\"}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    public static String sign(final byte[] privateScalar, final byte[] hash) {
        if (hash == null || hash.length != HASH_BYTES) {
            throw new IllegalArgumentException("ECDSA signs a 32-byte SHA-256 hash");
        }
        else {
            final var raw = lowS(signed(privateKey(privateScalar), statement(hash)));
            return Algorithms.typed(ID, raw);
        }
    }

    /**
     * Verifies a typed signature with a typed key. Both must be {@code ecdsa-p256-sha256}; an unknown, reserved or
     * mismatched identifier is refused before any curve arithmetic.
     */
    public static boolean verify(final String publicKey, final byte[] hash, final String signature) {
        try {
            final var typed = isPublicKey(publicKey) && isSignature(signature);
            if (!typed || hash == null || hash.length != HASH_BYTES) {
                return false;
            }
            else {
                final var point = P256.decode(Digests.unhex(Algorithms.body(publicKey)));
                final var raw = Digests.unhex(Algorithms.body(signature));
                return canonical(raw) && verified(publicKey(point), statement(hash), raw);
            }
        }
        catch (final RuntimeException | GeneralSecurityException failure) {
            return false;
        }
    }

    static boolean canonical(final byte[] signature) {
        final var r = new BigInteger(1, Arrays.copyOfRange(signature, 0, HASH_BYTES));
        final var s = new BigInteger(1, Arrays.copyOfRange(signature, HASH_BYTES, SIGNATURE_BYTES));
        final var rInRange = r.signum() > 0 && r.compareTo(P256.N) < 0;
        final var sLow = s.signum() > 0 && s.compareTo(P256.HALF_N) <= 0;
        return rInRange && sLow;
    }

    static byte[] lowS(final byte[] signature) {
        final var s = new BigInteger(1, Arrays.copyOfRange(signature, HASH_BYTES, SIGNATURE_BYTES));
        if (s.compareTo(P256.HALF_N) <= 0) {
            return signature;
        }
        else {
            final var flipped = P256.scalarBytes(P256.N.subtract(s));
            final var out = Arrays.copyOf(signature, SIGNATURE_BYTES);
            System.arraycopy(flipped, 0, out, HASH_BYTES, HASH_BYTES);
            return out;
        }
    }

    private static boolean verified(
        final PublicKey key,
        final byte[] message,
        final byte[] signature
    ) throws GeneralSecurityException {
        final var verifier = Signature.getInstance(ALGORITHM);
        verifier.initVerify(key);
        verifier.update(message);
        return verifier.verify(signature);
    }

    private static byte[] signed(final PrivateKey key, final byte[] message) {
        try {
            final var signer = Signature.getInstance(ALGORITHM);
            signer.initSign(key);
            signer.update(message);
            return signer.sign();
        }
        catch (final GeneralSecurityException failure) {
            throw new IllegalStateException("ECDSA P-256 sign failed", failure);
        }
    }

    static PrivateKey privateKey(final byte[] privateScalar) {
        try {
            final var spec = new ECPrivateKeySpec(P256.scalar(privateScalar), P256.SPEC);
            final var factory = KeyFactory.getInstance("EC");
            return factory.generatePrivate(spec);
        }
        catch (final GeneralSecurityException failure) {
            throw new IllegalStateException("P-256 private key is invalid", failure);
        }
    }

    static PublicKey publicKey(final ECPoint point) throws GeneralSecurityException {
        final var spec = new ECPublicKeySpec(point, P256.SPEC);
        final var factory = KeyFactory.getInstance("EC");
        return factory.generatePublic(spec);
    }
}
