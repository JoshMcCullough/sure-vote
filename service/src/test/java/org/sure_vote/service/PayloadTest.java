package org.sure_vote.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PayloadTest {
    @Test
    void chainEntryUsesTheCanonicalLayout() {
        final var timestamp = Instant.parse("2026-11-03T14:05:01.123Z");
        final var payload = Payloads.entry("abc", null, 0, timestamp, "n-1");
        final var expected = "{"
            + "\"voteHash\":\"abc\","
            + "\"previousEntryHash\":null,"
            + "\"index\":0,"
            + "\"timestamp\":\"2026-11-03T14:05:01.123Z\","
            + "\"nonce\":\"n-1\""
            + "}";
        assertEquals(expected, payload);
    }

    @Test
    void ballotHashIsTheEmptyVoteListPayload() {
        final var electionId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        final var precinctId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        final var deviceId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        final var placeId = UUID.fromString("00000000-0000-0000-0000-000000000004");
        final var recordedBy = UUID.fromString("00000000-0000-0000-0000-000000000005");
        final var candidateId = UUID.fromString("00000000-0000-0000-0000-000000000006");
        final var timestamp = Instant.parse("2026-11-03T12:00:00.000Z");
        final var accessHash = "ab".repeat(32);
        final var binding = Payloads.binding(
            electionId,
            precinctId,
            "in-person",
            deviceId,
            placeId,
            recordedBy,
            timestamp,
            accessHash
        );
        final var vote = Payloads.vote(binding, candidateId, "Y");
        final var voteHash = Digests.sha256Hex(vote);
        final var full = Payloads.ballot(
            electionId,
            precinctId,
            "in-person",
            deviceId,
            placeId,
            recordedBy,
            timestamp,
            accessHash,
            List.of(voteHash)
        );
        final var ballotHash = Digests.sha256Hex(full);
        assertNotEquals(binding, ballotHash);
        assertEquals(binding, Digests.sha256Hex(Payloads.ballot(
            electionId,
            precinctId,
            "in-person",
            deviceId,
            placeId,
            recordedBy,
            timestamp,
            accessHash,
            List.of()
        )));
    }
}
