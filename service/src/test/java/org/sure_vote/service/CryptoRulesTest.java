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
    private static final String RECIPIENT =
        "040217e617f0b6443928278f96999e69a23a4f2c152bdf6d6cdf66e5b80282d4ed"
            + "194a7debcb97712d2dda3ca85aa8765a56f45fc758599652f2897c65306e5794";
    private static final String HASH = "ab4e9d991e5d0874756969031a20998cd489b5a92c322ffaa61c9ee866f48c0c";
    private static final String LOW_S =
        "420c0e6f87d4e7e6d4e8f3c870a4cf61d33ab867dd8c356f308417263b22bb2c"
            + "7eb6bd3b6a5c8394136bfe417c5a243bea4d6a6f4d9d0ddfe690c0b87d0e526e";
    private static final String HIGH_S =
        "420c0e6f87d4e7e6d4e8f3c870a4cf61d33ab867dd8c356f308417263b22bb2c"
            + "814942c395a37c6cec9401be83a5dbc3d299903e597a90a50d290a0a7f54d2e3";

    @Test
    void sealedBoxMatchesTheReferenceEcies() {
        final var seed = Digests.unhex("11".repeat(32));
        final var ephemeral = Digests.unhex("22".repeat(32));
        final var message = "correct horse battery".getBytes(StandardCharsets.UTF_8);
        final var publicKey = ViewingBoxes.publicKey(seed);
        final var ephemeralPublic = ViewingBoxes.publicKey(ephemeral);
        final var sealed = ViewingBoxes.sealWith(message, publicKey, ephemeral);
        final var opened = ViewingBoxes.open(sealed, publicKey, seed);
        final var shared = ViewingBoxes.shared(ephemeral, publicKey);
        final var derived = ViewingBoxes.derive(shared, ephemeralPublic, publicKey);
        final var epk = Digests.hex(ephemeralPublic);
        final var body = Arrays.copyOfRange(sealed, 65, sealed.length);
        final var text = new String(opened, StandardCharsets.UTF_8);
        assertEquals(RECIPIENT, Digests.hex(publicKey));
        assertEquals(
            "04d65a93977caa3d1b081852ff57a79e465f1660577304baead505dd3a48589cf3"
                + "50185e895372df6221ea3a137557e473fddb6755f05bd507c3c533fce9c91285",
            epk
        );
        assertEquals("ccfc261f58193c98ca4ad4a53bbac6f0ee29bc4d48438090446908622ca79af6", Digests.hex(shared));
        assertEquals(
            "7437e583bcb29f1d8fe8ba420009e1c6732e3eae6cdd0ea8ec1aaef176a20b21065f6bcb112f26dd25285f85",
            Digests.hex(derived)
        );
        assertEquals("c6f59f87d61c957dccc1ba4ab131d03b69f7168fc859d24350b01b8dc0b91970f28cc0dbff", Digests.hex(body));
        assertEquals("correct horse battery", text);
    }

    @Test
    void aRandomSealRoundTrips() {
        final var seed = Digests.unhex("11".repeat(32));
        final var publicKey = ViewingBoxes.publicKey(seed);
        final var message = "another passphrase!!".getBytes(StandardCharsets.UTF_8);
        final var sealed = ViewingBoxes.seal(message, publicKey);
        final var opened = ViewingBoxes.open(sealed, publicKey, seed);
        final var text = new String(opened, StandardCharsets.UTF_8);
        assertEquals(81 + message.length, sealed.length);
        assertEquals("another passphrase!!", text);
    }

    @Test
    void aDamagedSealDoesNotOpen() {
        final var seed = Digests.unhex("11".repeat(32));
        final var publicKey = ViewingBoxes.publicKey(seed);
        final var message = "another passphrase!!".getBytes(StandardCharsets.UTF_8);
        final var sealed = this.damaged(ViewingBoxes.seal(message, publicKey));
        final var failure = assertThrows(
            ConstraintException.class,
            () -> ViewingBoxes.open(sealed, publicKey, seed)
        );
        assertEquals("viewing passphrase cannot be opened", failure.getMessage());
    }

    @Test
    void aReferenceSignatureVerifiesAndItsHighSTwinDoesNot() {
        final var hash = Digests.unhex(HASH);
        final var low = Signatures.verify(RECIPIENT, hash, LOW_S);
        final var high = Signatures.verify(RECIPIENT, hash, HIGH_S);
        assertTrue(low);
        assertFalse(high);
    }

    @Test
    void signaturesAreLowSAndRoundTrip() {
        final var key = Signatures.generate();
        final var hash = Digests.unhex(HASH);
        final var derived = Signatures.publicKey(key.seed());
        for (var round = 0; round < 32; round = round + 1) {
            final var signature = Signatures.sign(key.seed(), hash);
            final var raw = Digests.unhex(signature);
            assertEquals(128, signature.length());
            assertTrue(Signatures.canonical(raw));
            assertTrue(Signatures.verify(key.publicKey(), hash, signature));
        }
        assertEquals(key.publicKey(), derived);
        assertEquals(130, derived.length());
    }

    @Test
    void publicKeysMustBeUncompressedPointsOnTheCurve() {
        final var offCurve = RECIPIENT.substring(0, 128) + "95";
        final var compressed = "02" + RECIPIENT.substring(2, 66);
        final var hash = Digests.unhex(HASH);
        assertTrue(Signatures.isPublicKey(RECIPIENT));
        assertFalse(Signatures.isPublicKey(offCurve));
        assertFalse(Signatures.isPublicKey(compressed));
        assertFalse(Signatures.isPublicKey("00".repeat(65)));
        assertFalse(Signatures.verify(offCurve, hash, LOW_S));
    }

    @Test
    void mailHashMatchesArgon2id() {
        final var salt = Digests.unhex("33".repeat(32));
        final var hash = AccessCodes.mailHash("  correct horse battery  ", salt);
        assertEquals("eaa46dd3b19e60a65e2577ea88416dac5aca320b20db90d2016079bece0f1455", hash);
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
