package org.sure_vote.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KDF;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * {@code ecies-p256-hkdf-sha256-aes256gcm}: ephemeral ECDH over P-256, HKDF-SHA256, then AES-256-GCM.
 * The sealed bytes are the ephemeral public key (65 bytes, uncompressed SEC1), then the ciphertext, then the 16-byte
 * tag; stored, they are {@code ecies-p256-hkdf-sha256-aes256gcm:} then those bytes in hex. HKDF has an empty salt; its
 * info is {@code UTF-8(identifier) || 0x00 || ephemeral public || recipient public}, so the identifier is bound into
 * the key; it yields the 32-byte key and the 12-byte nonce. Each seal has a fresh ephemeral key, so the derived nonce
 * is never reused. Viewing public keys are {@code ecies-p256-hkdf-sha256-aes256gcm:04...}.
 */
final class ViewingBoxes {
    static final String ID = Algorithms.ECIES_P256;
    static final byte[] INFO = info();
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BYTES = 16;

    private ViewingBoxes() {
    }

    private static byte[] info() {
        final var id = ID.getBytes(StandardCharsets.US_ASCII);
        return Arrays.copyOf(id, id.length + 1);
    }

    static String typedPublicKey(final byte[] scalar) {
        return Algorithms.typed(ID, publicKey(scalar));
    }

    /**
     * True for {@code ecies-p256-hkdf-sha256-aes256gcm:04...}, an uncompressed point on the curve.
     */
    static boolean isPublicKey(final String publicKey) {
        return ID.equals(Algorithms.id(publicKey)) && P256.isPoint(Algorithms.body(publicKey));
    }

    /**
     * Seals to a typed viewing key and returns the typed box. Any other key identifier is refused.
     */
    static String seal(final byte[] message, final String recipient) {
        if (!isPublicKey(recipient)) {
            throw new ConstraintException("viewing public key is invalid");
        }
        else {
            final var sealed = seal(message, Digests.unhex(Algorithms.body(recipient)));
            return Algorithms.typed(ID, sealed);
        }
    }

    /**
     * Opens a typed box. A box with any identifier other than {@code ecies-p256-hkdf-sha256-aes256gcm} is refused.
     */
    static byte[] open(final String sealed, final byte[] recipientScalar) {
        final var hex = Algorithms.body(sealed);
        final var known = ID.equals(Algorithms.id(sealed)) && hex != null && Digests.isLowerHex(hex, hex.length());
        if (!known || hex.length() % 2 != 0 || recipientScalar == null) {
            throw new ConstraintException("viewing passphrase cannot be opened");
        }
        else {
            return open(Digests.unhex(hex), publicKey(recipientScalar), recipientScalar);
        }
    }

    static byte[] seal(final byte[] message, final byte[] recipientPublic) {
        final var ephemeral = Signatures.generate();
        final var scalar = ephemeral.seed();
        try {
            return sealWith(message, recipientPublic, scalar);
        }
        finally {
            wipe(scalar);
        }
    }

    static byte[] sealWith(final byte[] message, final byte[] recipientPublic, final byte[] ephemeralScalar) {
        final var ephemeralPublic = publicKey(ephemeralScalar);
        final var shared = shared(ephemeralScalar, recipientPublic);
        final var derived = derive(shared, ephemeralPublic, recipientPublic);
        final var boxed = gcm(Cipher.ENCRYPT_MODE, derived, message);
        wipe(shared);
        wipe(derived);
        return join(ephemeralPublic, boxed);
    }

    static byte[] open(final byte[] sealed, final byte[] recipientPublic, final byte[] recipientScalar) {
        final var shortest = P256.POINT_BYTES + TAG_BYTES;
        if (sealed == null || recipientPublic == null || recipientScalar == null || sealed.length < shortest) {
            throw new ConstraintException("viewing passphrase cannot be opened");
        }
        else {
            return opened(sealed, recipientPublic, recipientScalar);
        }
    }

    static byte[] publicKey(final byte[] scalar) {
        return P256.publicKey(scalar);
    }

    static byte[] shared(final byte[] scalar, final byte[] peerPublic) {
        try {
            final var point = P256.decode(peerPublic);
            if (point == null) {
                throw new ConstraintException("viewing passphrase cannot be opened");
            }
            else {
                return agreed((ECPrivateKey) Signatures.privateKey(scalar), (ECPublicKey) Signatures.publicKey(point));
            }
        }
        catch (final GeneralSecurityException | RuntimeException failure) {
            throw new ConstraintException("viewing passphrase cannot be opened");
        }
    }

    static byte[] derive(final byte[] shared, final byte[] ephemeralPublic, final byte[] recipientPublic) {
        try {
            final var info = join(INFO, join(ephemeralPublic, recipientPublic));
            final var kdf = KDF.getInstance("HKDF-SHA256");
            final var extract = HKDFParameterSpec.ofExtract();
            final var withKey = extract.addIKM(shared);
            final var withSalt = withKey.addSalt(new byte[0]);
            final var spec = withSalt.thenExpand(info, KEY_BYTES + NONCE_BYTES);
            return kdf.deriveData(spec);
        }
        catch (final GeneralSecurityException failure) {
            throw new IllegalStateException("HKDF-SHA256 is required", failure);
        }
    }

    private static byte[] agreed(final ECPrivateKey key, final ECPublicKey peer) throws GeneralSecurityException {
        final var agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(key);
        agreement.doPhase(peer, true);
        return agreement.generateSecret();
    }

    private static byte[] opened(final byte[] sealed, final byte[] recipientPublic, final byte[] recipientScalar) {
        final var ephemeralPublic = Arrays.copyOfRange(sealed, 0, P256.POINT_BYTES);
        final var boxed = Arrays.copyOfRange(sealed, P256.POINT_BYTES, sealed.length);
        final var shared = shared(recipientScalar, ephemeralPublic);
        final var derived = derive(shared, ephemeralPublic, recipientPublic);
        try {
            return gcm(Cipher.DECRYPT_MODE, derived, boxed);
        }
        finally {
            wipe(shared);
            wipe(derived);
        }
    }

    private static byte[] gcm(final int mode, final byte[] derived, final byte[] input) {
        final var key = new SecretKeySpec(derived, 0, KEY_BYTES, "AES");
        final var nonce = new GCMParameterSpec(8 * TAG_BYTES, derived, KEY_BYTES, NONCE_BYTES);
        try {
            final var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, key, nonce);
            return cipher.doFinal(input);
        }
        catch (final GeneralSecurityException failure) {
            throw new ConstraintException("viewing passphrase cannot be opened");
        }
    }

    private static byte[] join(final byte[] left, final byte[] right) {
        final var joined = new byte[left.length + right.length];
        System.arraycopy(left, 0, joined, 0, left.length);
        System.arraycopy(right, 0, joined, left.length, right.length);
        return joined;
    }

    static void wipe(final byte[] bytes) {
        if (bytes != null) {
            Arrays.fill(bytes, (byte) 0);
        }
    }
}
