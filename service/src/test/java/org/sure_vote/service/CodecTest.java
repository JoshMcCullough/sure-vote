package org.sure_vote.service;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.sure_vote.entity.BallotType;
import org.sure_vote.entity.VoteChain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CodecTest {
    @Test
    void canonicalJsonEscapesAndEncodesAbsents() {
        final var quoted = CanonicalJson.quote("a\"b\\c\n");
        final var absent = CanonicalJson.nullableString(null);
        final var missingId = CanonicalJson.uuid(null);
        final var flag = CanonicalJson.bool(false);
        assertEquals("\"a\\\"b\\\\c\\u000a\"", quoted);
        assertEquals("null", absent);
        assertEquals("null", missingId);
        assertEquals("false", flag);
    }

    @Test
    void digestsRejectBadHex() {
        final var upper = Digests.isLowerHex("AB", 2);
        final var missing = Digests.isLowerHex(null, 64);
        final var odd = assertThrows(IllegalArgumentException.class, () -> Digests.unhex("abc"));
        final var empty = assertThrows(IllegalArgumentException.class, () -> Digests.unhex(null));
        final var letter = assertThrows(IllegalArgumentException.class, () -> Digests.unhex("ag"));
        assertFalse(upper);
        assertFalse(missing);
        assertEquals("lowercase hex is required", odd.getMessage());
        assertEquals("lowercase hex is required", empty.getMessage());
        assertEquals("lowercase hex is required", letter.getMessage());
    }

    @Test
    void aWrongSignatureDoesNotVerify() {
        final var key = Signatures.generate();
        final var hash = new byte[32];
        final var publicKey = key.publicKey();
        final var wrong = "00".repeat(64);
        final var verified = Signatures.verify(publicKey, hash, wrong);
        assertFalse(verified);
    }

    @Test
    void sealRequiresSelections() {
        final var request = new SealRequest(
            null,
            null,
            null,
            null,
            BallotType.IN_PERSON,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
        final var failure = assertThrows(
            ConstraintException.class,
            () -> Sealer.seal(null, request, null, null)
        );
        assertEquals("selections are required", failure.getMessage());
    }

    @Test
    void sealRequiresTheRaceChain() {
        final var failure = assertThrows(ConstraintException.class, this::sealWithoutChain);
        assertEquals("A race has one vote chain", failure.getMessage());
    }

    private void sealWithoutChain() {
        final var world = SampleElection.fptp();
        final var request = this.request(world);
        this.dropChain(world);
        Sealer.seal(world.repository, request, world.deviceKey, world.importKey);
    }

    private SealRequest request(final SampleElection world) {
        final var raw = AccessCodes.generate();
        final var hash = AccessCodes.inPersonHash(raw);
        final var selection = new SealSelection(world.slotA, "Y", "n-1");
        return new SealRequest(
            world.tenantId,
            world.actor,
            world.electionId,
            world.precinctId,
            BallotType.IN_PERSON,
            world.deviceId,
            world.placeId,
            world.deviceId,
            world.ballotTimestamp,
            hash,
            world.entryTimestamp,
            List.of(selection)
        );
    }

    private void dropChain(final SampleElection world) {
        final var rows = world.repository.list(VoteChain.class);
        final var chain = rows.getFirst();
        final var next = Copies.chain(chain);
        next.setRaceId(UUID.randomUUID());
        world.repository.replace(next);
    }
}
