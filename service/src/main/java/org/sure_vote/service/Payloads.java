package org.sure_vote.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Canonical payloads from docs/verification.md.
 * ballotHash in a vote is the ballot payload hashed with voteHashes empty, not Ballot.hash.
 */
public final class Payloads {
    private Payloads() {
    }

    public static String ballot(
        final UUID electionId,
        final UUID votingPrecinctId,
        final String type,
        final UUID recordingDeviceId,
        final UUID recordingPlaceId,
        final UUID recordedBy,
        final Instant timestamp,
        final String voterAccessHash,
        final List<String> voteHashes
    ) {
        final var fields = new ArrayList<String>();
        fields.add(CanonicalJson.field("electionId", CanonicalJson.uuid(electionId)));
        fields.add(CanonicalJson.field("votingPrecinctId", CanonicalJson.uuid(votingPrecinctId)));
        fields.add(CanonicalJson.field("type", CanonicalJson.quote(type)));
        fields.add(CanonicalJson.field("recordingDeviceId", CanonicalJson.uuid(recordingDeviceId)));
        fields.add(CanonicalJson.field("recordingPlaceId", CanonicalJson.uuid(recordingPlaceId)));
        fields.add(CanonicalJson.field("recordedBy", CanonicalJson.uuid(recordedBy)));
        fields.add(CanonicalJson.field("timestamp", CanonicalJson.timestamp(timestamp)));
        fields.add(CanonicalJson.field("voterAccessHash", CanonicalJson.quote(voterAccessHash)));
        fields.add(CanonicalJson.field("voteHashes", CanonicalJson.strings(voteHashes)));
        return CanonicalJson.object(fields);
    }

    public static String binding(
        final UUID electionId,
        final UUID votingPrecinctId,
        final String type,
        final UUID recordingDeviceId,
        final UUID recordingPlaceId,
        final UUID recordedBy,
        final Instant timestamp,
        final String voterAccessHash
    ) {
        final var payload = ballot(
            electionId,
            votingPrecinctId,
            type,
            recordingDeviceId,
            recordingPlaceId,
            recordedBy,
            timestamp,
            voterAccessHash,
            List.of()
        );
        return Digests.sha256Hex(payload);
    }

    public static String vote(final String ballotHash, final UUID raceCandidateId, final String value) {
        final var fields = new ArrayList<String>();
        fields.add(CanonicalJson.field("ballotHash", CanonicalJson.quote(ballotHash)));
        fields.add(CanonicalJson.field("raceCandidateId", CanonicalJson.uuid(raceCandidateId)));
        fields.add(CanonicalJson.field("value", CanonicalJson.quote(value)));
        return CanonicalJson.object(fields);
    }

    public static String entry(
        final String voteHash,
        final String previousEntryHash,
        final int index,
        final Instant timestamp,
        final String nonce
    ) {
        final var fields = new ArrayList<String>();
        fields.add(CanonicalJson.field("voteHash", CanonicalJson.quote(voteHash)));
        fields.add(CanonicalJson.field("previousEntryHash", CanonicalJson.nullableString(previousEntryHash)));
        fields.add(CanonicalJson.field("index", CanonicalJson.number(index)));
        fields.add(CanonicalJson.field("timestamp", CanonicalJson.timestamp(timestamp)));
        fields.add(CanonicalJson.field("nonce", CanonicalJson.quote(nonce)));
        return CanonicalJson.object(fields);
    }

    public static String emptyHead(final UUID raceId) {
        final var fields = new ArrayList<String>();
        fields.add(CanonicalJson.field("raceId", CanonicalJson.uuid(raceId)));
        fields.add(CanonicalJson.field("length", CanonicalJson.number(0)));
        return CanonicalJson.object(fields);
    }

    public static String validation(
        final UUID voteChainId,
        final UUID validatorId,
        final String validatedThroughHash,
        final int length,
        final String result,
        final Instant dateValidated,
        final String notes,
        final String evidenceHash
    ) {
        final var fields = new ArrayList<String>();
        fields.add(CanonicalJson.field("voteChainId", CanonicalJson.uuid(voteChainId)));
        fields.add(CanonicalJson.field("validatorId", CanonicalJson.uuid(validatorId)));
        fields.add(CanonicalJson.field("validatedThroughHash", CanonicalJson.quote(validatedThroughHash)));
        fields.add(CanonicalJson.field("length", CanonicalJson.number(length)));
        fields.add(CanonicalJson.field("result", CanonicalJson.quote(result)));
        fields.add(CanonicalJson.field("dateValidated", CanonicalJson.timestamp(dateValidated)));
        fields.add(CanonicalJson.field("notes", CanonicalJson.nullableString(notes)));
        fields.add(CanonicalJson.field("evidenceHash", CanonicalJson.nullableString(evidenceHash)));
        return CanonicalJson.object(fields);
    }

    public static String restoreIncident(
        final UUID electionId,
        final List<ChainHead> witnessedHeads,
        final List<ChainHead> restoredHeads,
        final String reason,
        final Instant timestamp
    ) {
        final var fields = new ArrayList<String>();
        fields.add(CanonicalJson.field("electionId", CanonicalJson.uuid(electionId)));
        fields.add(CanonicalJson.field("witnessedHeads", heads(witnessedHeads)));
        fields.add(CanonicalJson.field("restoredHeads", heads(restoredHeads)));
        fields.add(CanonicalJson.field("reason", CanonicalJson.quote(reason)));
        fields.add(CanonicalJson.field("timestamp", CanonicalJson.timestamp(timestamp)));
        return CanonicalJson.object(fields);
    }

    private static String heads(final List<ChainHead> heads) {
        final var encoded = new ArrayList<String>();
        for (final var head : heads) {
            encoded.add(head(head));
        }
        return CanonicalJson.array(encoded);
    }

    private static String head(final ChainHead head) {
        final var fields = new ArrayList<String>();
        fields.add(CanonicalJson.field("raceId", CanonicalJson.uuid(head.raceId())));
        fields.add(CanonicalJson.field("length", CanonicalJson.number(head.length())));
        fields.add(CanonicalJson.field("headHash", CanonicalJson.quote(head.headHash())));
        return CanonicalJson.object(fields);
    }
}
