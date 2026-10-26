package org.sure_vote.service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.sure_vote.entity.Person;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryptoRulesTest {
    // Reference values from docs/vectors/vectors.json, made by docs/vectors/generate.py with pyca/cryptography
    // (OpenSSL, RFC 6979 deterministic ECDSA) and argon2-cffi, not by this code.
    private static final String POINT =
        "040217e617f0b6443928278f96999e69a23a4f2c152bdf6d6cdf66e5b80282d4ed"
            + "194a7debcb97712d2dda3ca85aa8765a56f45fc758599652f2897c65306e5794";
    private static final String SIGNING_KEY = "ecdsa-p256-sha256:" + POINT;
    private static final String VIEWING_KEY = "ecies-p256-hkdf-sha256-aes256gcm:" + POINT;
    private static final String HASH = "ab4e9d991e5d0874756969031a20998cd489b5a92c322ffaa61c9ee866f48c0c";
    private static final String R = "ff7327be8e779ae277a0d4e7f7ca0f6539993f846ef82c58cbcebfa5515408fe";
    private static final String LOW_S =
        "ecdsa-p256-sha256:" + R + "110cc0fb16b38aa8d9a579edea6524d3ac425b4e40aa582ae6fe906372539e56";
    private static final String HIGH_S =
        "ecdsa-p256-sha256:" + R + "eef33f03e94c7558265a8612159adb2c10a49f5f666d465a0cbb3a5f8a0f86fb";
    private static final String EPHEMERAL =
        "04d65a93977caa3d1b081852ff57a79e465f1660577304baead505dd3a48589cf3"
            + "50185e895372df6221ea3a137557e473fddb6755f05bd507c3c533fce9c91285";
    private static final String BODY = "c4d82225033d65176d059e623fc61fcae990c34772efb4db6294512fb83b13adf1f9c9bbe7";

    @Test
    void sealedBoxMatchesTheReferenceEcies() {
        final var seed = Digests.unhex("11".repeat(32));
        final var ephemeral = Digests.unhex("22".repeat(32));
        final var message = "correct horse battery".getBytes(StandardCharsets.UTF_8);
        final var publicKey = ViewingBoxes.publicKey(seed);
        final var ephemeralPublic = ViewingBoxes.publicKey(ephemeral);
        final var sealed = ViewingBoxes.sealWith(message, publicKey, ephemeral);
        final var typed = Algorithms.typed(ViewingBoxes.ID, sealed);
        final var opened = ViewingBoxes.open(typed, seed);
        final var shared = ViewingBoxes.shared(ephemeral, publicKey);
        final var derived = ViewingBoxes.derive(shared, ephemeralPublic, publicKey);
        final var body = Arrays.copyOfRange(sealed, 65, sealed.length);
        final var text = new String(opened, StandardCharsets.UTF_8);
        assertEquals(VIEWING_KEY, ViewingBoxes.typedPublicKey(seed));
        assertEquals(EPHEMERAL, Digests.hex(ephemeralPublic));
        assertEquals("ccfc261f58193c98ca4ad4a53bbac6f0ee29bc4d48438090446908622ca79af6", Digests.hex(shared));
        assertEquals(
            "fe132f5dfc2eb5256ceafc455821fe92b045438ff3f8731a333fbecf08d7e4683af39a8aa6f13a1ce0649402",
            Digests.hex(derived)
        );
        assertEquals(BODY, Digests.hex(body));
        assertEquals("ecies-p256-hkdf-sha256-aes256gcm:" + EPHEMERAL + BODY, typed);
        assertEquals("correct horse battery", text);
    }

    @Test
    void aRandomSealRoundTrips() {
        final var seed = Digests.unhex("11".repeat(32));
        final var message = "another passphrase!!".getBytes(StandardCharsets.UTF_8);
        final var sealed = ViewingBoxes.seal(message, VIEWING_KEY);
        final var opened = ViewingBoxes.open(sealed, seed);
        final var text = new String(opened, StandardCharsets.UTF_8);
        assertEquals("ecies-p256-hkdf-sha256-aes256gcm:".length() + 2 * (81 + message.length), sealed.length());
        assertEquals("another passphrase!!", text);
    }

    @Test
    void aDamagedSealDoesNotOpen() {
        final var seed = Digests.unhex("11".repeat(32));
        final var message = "another passphrase!!".getBytes(StandardCharsets.UTF_8);
        final var sealed = ViewingBoxes.seal(message, Digests.unhex(POINT));
        final var typed = Algorithms.typed(ViewingBoxes.ID, this.damaged(sealed));
        this.assertUnopened(typed, seed);
    }

    @Test
    void aBoxUnderAnotherIdentifierDoesNotOpen() {
        final var seed = Digests.unhex("11".repeat(32));
        final var body = EPHEMERAL + BODY;
        this.assertUnopened(Algorithms.HYBRID_SEAL + ":" + body, seed);
        this.assertUnopened("ecies-p256-hkdf-sha512-aes256gcm:" + body, seed);
        this.assertUnopened(body, seed);
        assertThrows(ConstraintException.class, () -> ViewingBoxes.seal(new byte[1], SIGNING_KEY));
        assertFalse(ViewingBoxes.isPublicKey(SIGNING_KEY));
    }

    @Test
    void aReferenceSignatureVerifiesAndItsHighSTwinDoesNot() {
        final var hash = Digests.unhex(HASH);
        final var low = Signatures.verify(SIGNING_KEY, hash, LOW_S);
        final var high = Signatures.verify(SIGNING_KEY, hash, HIGH_S);
        final var statement = new String(Signatures.statement(hash), StandardCharsets.UTF_8);
        assertTrue(low);
        assertFalse(high);
        assertEquals("{\"alg\":\"ecdsa-p256-sha256\",\"hash\":\"" + HASH + "\"}", statement);
    }

    @Test
    void verifiersRefuseUnknownReservedAndMismatchedIdentifiers() {
        final var hash = Digests.unhex(HASH);
        final var raw = Algorithms.body(LOW_S);
        assertFalse(Signatures.verify(SIGNING_KEY, hash, Algorithms.HYBRID_SIGNATURE + ":" + raw));
        assertFalse(Signatures.verify(SIGNING_KEY, hash, "ecdsa-p384-sha384:" + raw));
        assertFalse(Signatures.verify(SIGNING_KEY, hash, raw));
        assertFalse(Signatures.verify(VIEWING_KEY, hash, LOW_S));
        assertFalse(Signatures.verify(POINT, hash, LOW_S));
        assertFalse(Signatures.verify(Algorithms.HYBRID_SIGNATURE + ":" + POINT, hash, LOW_S));
        assertFalse(Algorithms.accepts(Algorithms.HYBRID_SIGNATURE, Algorithms.Kind.SIGNATURE));
        assertFalse(Algorithms.accepts(Algorithms.ECIES_P256, Algorithms.Kind.SIGNATURE));
        assertFalse(Algorithms.accepts("pbkdf2-hmac-sha256", Algorithms.Kind.ACCESS_HASH));
        assertTrue(Algorithms.accepts(Algorithms.ECDSA_P256_SHA256, Algorithms.Kind.SIGNATURE));
    }

    @Test
    void aSignatureOverTheBareHashDoesNotVerify() {
        final var seed = Digests.unhex("11".repeat(32));
        final var hash = Digests.unhex(HASH);
        final var signer = Signatures.privateKey(seed);
        final var bare = this.bareSignature(signer, hash);
        assertFalse(Signatures.verify(SIGNING_KEY, hash, Algorithms.typed(Signatures.ID, bare)));
    }

    @Test
    void signaturesAreLowSAndRoundTrip() {
        final var key = Signatures.generate();
        final var hash = Digests.unhex(HASH);
        final var derived = Signatures.publicKey(key.seed());
        for (var round = 0; round < 32; round = round + 1) {
            final var signature = Signatures.sign(key.seed(), hash);
            final var raw = Digests.unhex(Algorithms.body(signature));
            assertTrue(Signatures.isSignature(signature));
            assertTrue(Signatures.canonical(raw));
            assertTrue(Signatures.verify(key.publicKey(), hash, signature));
        }
        assertEquals(key.publicKey(), derived);
        assertEquals("ecdsa-p256-sha256:".length() + 130, derived.length());
    }

    @Test
    void publicKeysMustBeTypedUncompressedPointsOnTheCurve() {
        final var offCurve = SIGNING_KEY.substring(0, SIGNING_KEY.length() - 2) + "95";
        final var compressed = "ecdsa-p256-sha256:02" + POINT.substring(2, 66);
        final var hash = Digests.unhex(HASH);
        assertTrue(Signatures.isPublicKey(SIGNING_KEY));
        assertTrue(ViewingBoxes.isPublicKey(VIEWING_KEY));
        assertFalse(Signatures.isPublicKey(POINT));
        assertFalse(Signatures.isPublicKey(offCurve));
        assertFalse(Signatures.isPublicKey(compressed));
        assertFalse(Signatures.isPublicKey("ecdsa-p256-sha256:" + "00".repeat(65)));
        assertFalse(Signatures.verify(offCurve, hash, LOW_S));
    }

    @Test
    void accessHashesNameTheirAlgorithmAndParameters() {
        final var salt = Digests.unhex("33".repeat(32));
        final var mail = AccessCodes.mailHash("  correct horse battery  ", salt);
        final var inPerson = AccessCodes.inPersonHash(Digests.unhex("44".repeat(32)));
        final var hex = "eaa46dd3b19e60a65e2577ea88416dac5aca320b20db90d2016079bece0f1455";
        assertEquals("argon2id-v13:m=65536,t=3,p=1:" + hex, mail);
        assertEquals("sha256:bb391415c05e39d77ca17381d3be3f7d0cd5e5332e5a579311adaa0aa62106e9", inPerson);
        assertTrue(AccessCodes.isAccessHash(mail, true));
        assertTrue(AccessCodes.isAccessHash(inPerson, false));
        assertFalse(AccessCodes.isAccessHash(mail, false));
        assertFalse(AccessCodes.isAccessHash(inPerson, true));
        assertFalse(AccessCodes.isAccessHash("argon2id-v13:m=4096,t=3,p=1:" + hex, true));
        assertFalse(AccessCodes.isAccessHash(hex, false));
    }

    @Test
    void passphraseRulesRejectShortNamesAndCommonAnswers() {
        final var person = this.person();
        final var accepted = Passphrases.require("correct horse battery", person);
        this.assertPassphrase(person, "short-secret", "viewing passphrase must be at least 16 characters");
        this.assertPassphrase(
            person,
            "  Ada Example Voter  ",
            "viewing passphrase must not match the voter's email, phone, or name"
        );
        this.assertPassphrase(
            person,
            "ada.voter@example.com",
            "viewing passphrase must not match the voter's email, phone, or name"
        );
        this.assertPassphrase(
            person,
            "555-0100-0000-999",
            "viewing passphrase must not match the voter's email, phone, or name"
        );
        this.assertPassphrase(person, "mother's maiden name", "viewing passphrase is a common question answer");
        this.assertPassphrase(person, "my first pet fluffy", "viewing passphrase is a common question answer");
        assertEquals("correct horse battery", accepted);
        assertFalse(accepted.contains("Ada"));
    }

    private void assertUnopened(final String sealed, final byte[] seed) {
        final var failure = assertThrows(ConstraintException.class, () -> ViewingBoxes.open(sealed, seed));
        assertEquals("viewing passphrase cannot be opened", failure.getMessage());
    }

    private byte[] bareSignature(final java.security.PrivateKey key, final byte[] hash) {
        try {
            final var signer = java.security.Signature.getInstance("NONEwithECDSAinP1363Format");
            signer.initSign(key);
            signer.update(hash);
            return Signatures.lowS(signer.sign());
        }
        catch (final java.security.GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private byte[] damaged(final byte[] sealed) {
        sealed[70] = (byte) (sealed[70] ^ 0x01);
        return sealed;
    }

    private Person person() {
        final var person = new Person();
        person.setFirstName("Ada");
        person.setMiddleName("Example");
        person.setLastName("Voter");
        person.setFullName("Ada Example Voter");
        person.setEmail("Ada.Voter@Example.com");
        person.setPhoneNumber("555-0100-0000-999");
        return person;
    }

    private void assertPassphrase(final Person person, final String passphrase, final String message) {
        final var failure = assertThrows(
            ConstraintException.class,
            () -> Passphrases.require(passphrase, person)
        );
        assertEquals(message, failure.getMessage());
    }
}
