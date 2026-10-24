package org.sure_vote.service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.sure_vote.entity.Person;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CryptoRulesTest {
    @Test
    void sealedBoxMatchesLibsodium() {
        final var seed = Digests.unhex("11".repeat(32));
        final var ephemeral = Digests.unhex("22".repeat(32));
        final var message = "correct horse battery".getBytes(StandardCharsets.UTF_8);
        final var publicKey = ViewingBoxes.publicKey(seed);
        final var ephemeralPublic = ViewingBoxes.publicKey(ephemeral);
        final var sealed = ViewingBoxes.sealWith(message, publicKey, ephemeral);
        final var opened = ViewingBoxes.open(sealed, publicKey, seed);
        final var shared = ViewingBoxes.shared(ephemeral, publicKey);
        final var boxKey = ViewingBoxes.boxKey(shared);
        final var nonce = ViewingBoxes.nonce(ephemeralPublic, publicKey);
        final var pk = Digests.hex(publicKey);
        final var epk = Digests.hex(ephemeralPublic);
        final var sharedHex = Digests.hex(shared);
        final var boxHex = Digests.hex(boxKey);
        final var nonceHex = Digests.hex(nonce);
        final var body = Arrays.copyOfRange(sealed, 32, sealed.length);
        final var ciphertext = Digests.hex(body);
        final var text = new String(opened, StandardCharsets.UTF_8);
        assertEquals("7b4e909bbe7ffe44c465a220037d608ee35897d31ef972f07f74892cb0f73f13", pk);
        assertEquals("0faa684ed28867b97f4a6a2dee5df8ce974e76b7018e3f22a1c4cf2678570f20", epk);
        assertEquals("9e004098efc091d4ec2663b4e9f5cfd4d7064571690b4bea97ab146ab9f35056", sharedHex);
        assertEquals("8abe7c8ae6800b76d4f5986b04b77f6e9b4f332403c90df02428336e8bd9b525", boxHex);
        assertEquals("631ab57d2fbc4c0095de2610535cc8b1caa95c94d0800320", nonceHex);
        assertEquals("c0c51035f02eecc6b054d245e089722ab9b4cc027cfee32d3d863a4964322cb3f97454b58d", ciphertext);
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
        assertEquals(48 + message.length, sealed.length);
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
        sealed[40] = (byte) (sealed[40] ^ 0x01);
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
