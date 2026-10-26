package org.sure_vote.service;

import java.math.BigInteger;
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
 * ECDSA over NIST P-256. The signed digest is the 32-byte SHA-256 hash the caller passes, used as the ECDSA digest
 * directly, so a signature over {@code hash = SHA-256(payload)} is a standard ECDSA-SHA256 signature over the
 * payload. Signatures are IEEE P1363 {@code r || s} (64 bytes) with low S. Public keys are uncompressed SEC1.
 */
public final class Signatures {
    private static final String ALGORITHM = "NONEwithECDSAinP1363Format";
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
        return new SigningKey(Digests.hex(encoded), scalar);
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
        return Digests.hex(P256.publicKey(privateScalar));
    }

    public static boolean isPublicKey(final String publicKeyHex) {
        if (!Digests.isLowerHex(publicKeyHex, 2 * P256.POINT_BYTES)) {
            return false;
        }
        else {
            final var raw = Digests.unhex(publicKeyHex);
            return P256.decode(raw) != null;
        }
    }

    public static String sign(final byte[] privateScalar, final byte[] hash) {
        if (hash == null || hash.length != HASH_BYTES) {
            throw new IllegalArgumentException("ECDSA signs a 32-byte SHA-256 hash");
        }
        else {
            return Digests.hex(lowS(signed(privateKey(privateScalar), hash)));
        }
    }

    public static boolean verify(final String publicKeyHex, final byte[] hash, final String signatureHex) {
        try {
            final var hexes = isPublicKey(publicKeyHex) && Digests.isLowerHex(signatureHex, 2 * SIGNATURE_BYTES);
            if (!hexes || hash == null || hash.length != HASH_BYTES) {
                return false;
            }
            else {
                final var point = P256.decode(Digests.unhex(publicKeyHex));
                final var signature = Digests.unhex(signatureHex);
                return canonical(signature) && verified(publicKey(point), hash, signature);
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
        final byte[] hash,
        final byte[] signature
    ) throws GeneralSecurityException {
        final var verifier = Signature.getInstance(ALGORITHM);
        verifier.initVerify(key);
        verifier.update(hash);
        return verifier.verify(signature);
    }

    private static byte[] signed(final PrivateKey key, final byte[] hash) {
        try {
            final var signer = Signature.getInstance(ALGORITHM);
            signer.initSign(key);
            signer.update(hash);
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
