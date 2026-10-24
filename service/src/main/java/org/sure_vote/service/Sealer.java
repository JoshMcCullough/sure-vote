package org.sure_vote.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.sure_vote.entity.Race;
import org.sure_vote.entity.RaceCandidate;
import org.sure_vote.entity.VoteChain;
import org.sure_vote.repository.SureVoteRepository;

/**
 * Reference sealer for an offline machine and the import workstation.
 * The caller holds the device and import private keys. This service does not.
 */
public final class Sealer {
    private Sealer() {
    }

    public static ImportBallot seal(
        final SureVoteRepository repository,
        final SealRequest request,
        final SigningKey deviceKey,
        final SigningKey importKey
    ) {
        if (request.selections() == null) {
            throw new ConstraintException("selections are required");
        }
        else {
            return sealSelections(repository, request, deviceKey, importKey);
        }
    }

    private static ImportBallot sealSelections(
        final SureVoteRepository repository,
        final SealRequest request,
        final SigningKey deviceKey,
        final SigningKey importKey
    ) {
        final var ordered = ordered(repository, request.selections());
        final var votes = new ArrayList<ImportVote>();
        final var hashes = new ArrayList<String>();
        final var heads = new HashMap<UUID, Head>();
        final var type = request.type();
        final var typeWire = type.wire();
        final var binding = Payloads.binding(
            request.electionId(),
            request.votingPrecinctId(),
            typeWire,
            request.recordingDeviceId(),
            request.recordingPlaceId(),
            request.recordedBy(),
            request.timestamp(),
            request.voterAccessHash()
        );
        for (final var draft : ordered) {
            append(repository, request, deviceKey, importKey, binding, draft, votes, hashes, heads);
        }
        return ballot(request, typeWire, hashes, votes, deviceKey);
    }

    private static void append(
        final SureVoteRepository repository,
        final SealRequest request,
        final SigningKey deviceKey,
        final SigningKey importKey,
        final String binding,
        final Draft draft,
        final List<ImportVote> votes,
        final List<String> hashes,
        final Map<UUID, Head> heads
    ) {
        final var head = headFor(repository, heads, draft.raceId);
        final var votePayload = Payloads.vote(binding, draft.candidateId, draft.value);
        final var voteHash = Digests.sha256Hex(votePayload);
        final var voteRaw = Digests.unhex(voteHash);
        final var deviceSeed = deviceKey.seed();
        final var voteSignature = Signatures.sign(deviceSeed, voteRaw);
        final var entryPayload = Payloads.entry(
            voteHash,
            head.previous,
            head.index,
            request.entryTimestamp(),
            draft.nonce
        );
        final var entryHash = Digests.sha256Hex(entryPayload);
        final var entryRaw = Digests.unhex(entryHash);
        final var importSeed = importKey.seed();
        final var entrySignature = Signatures.sign(importSeed, entryRaw);
        final var vote = new ImportVote(
            draft.candidateId,
            draft.value,
            voteSignature,
            request.entryTimestamp(),
            draft.nonce,
            entrySignature
        );
        hashes.add(voteHash);
        votes.add(vote);
        heads.put(draft.raceId, head.advance(entryHash));
    }

    private static ImportBallot ballot(
        final SealRequest request,
        final String typeWire,
        final List<String> hashes,
        final List<ImportVote> votes,
        final SigningKey deviceKey
    ) {
        final var payload = Payloads.ballot(
            request.electionId(),
            request.votingPrecinctId(),
            typeWire,
            request.recordingDeviceId(),
            request.recordingPlaceId(),
            request.recordedBy(),
            request.timestamp(),
            request.voterAccessHash(),
            hashes
        );
        final var hash = Digests.sha256Hex(payload);
        final var raw = Digests.unhex(hash);
        final var seed = deviceKey.seed();
        final var signature = Signatures.sign(seed, raw);
        return new ImportBallot(
            request.tenantId(),
            request.actorId(),
            request.electionId(),
            request.votingPrecinctId(),
            request.type(),
            request.recordingDeviceId(),
            request.recordingPlaceId(),
            request.recordedBy(),
            request.timestamp(),
            request.voterAccessHash(),
            signature,
            votes
        );
    }

    private static List<Draft> ordered(final SureVoteRepository repository, final List<SealSelection> selections) {
        final var drafted = new ArrayList<Draft>();
        for (final var selection : selections) {
            drafted.add(draft(repository, selection));
        }
        drafted.sort((left, right) -> Draft.order(left, right));
        return drafted;
    }

    private static Draft draft(final SureVoteRepository repository, final SealSelection selection) {
        final var slot = find(repository, RaceCandidate.class, selection.raceCandidateId(), "Missing RaceCandidate");
        final var race = find(repository, Race.class, slot.getRaceId(), "Missing Race");
        return new Draft(
            race.getId(),
            slot.getId(),
            selection.value(),
            VoteOrder.rank(selection.value()),
            selection.nonce()
        );
    }

    private static <T extends org.sure_vote.entity.Identifiable> T find(
        final SureVoteRepository repository,
        final Class<T> type,
        final UUID id,
        final String message
    ) {
        final var found = repository.find(type, id);
        if (found.isEmpty()) {
            throw new ConstraintException(message);
        }
        else {
            return found.get();
        }
    }

    private static Head headFor(
        final SureVoteRepository repository,
        final Map<UUID, Head> heads,
        final UUID raceId
    ) {
        final var local = heads.get(raceId);
        if (local != null) {
            return local;
        }
        else {
            return headFromStore(repository, raceId);
        }
    }

    private static Head headFromStore(final SureVoteRepository repository, final UUID raceId) {
        final var chain = findChain(repository, raceId);
        if (chain.getLength() == 0) {
            return new Head(null, 0);
        }
        else {
            return new Head(chain.getHash(), chain.getLength());
        }
    }

    private static VoteChain findChain(final SureVoteRepository repository, final UUID raceId) {
        final var matched = new ArrayList<VoteChain>();
        final var rows = repository.list(VoteChain.class);
        for (final var row : rows) {
            collectChain(matched, row, raceId);
        }
        if (matched.size() != 1) {
            throw new ConstraintException("A race has one vote chain");
        }
        else {
            return matched.getFirst();
        }
    }

    private static void collectChain(final List<VoteChain> matched, final VoteChain row, final UUID raceId) {
        final var rowRace = row.getRaceId();
        if (raceId.equals(rowRace)) {
            matched.add(row);
        }
    }

    private static final class Draft {
        private final UUID raceId;
        private final UUID candidateId;
        private final String value;
        private final int rank;
        private final String nonce;

        private Draft(
            final UUID raceId,
            final UUID candidateId,
            final String value,
            final int rank,
            final String nonce
        ) {
            this.raceId = raceId;
            this.candidateId = candidateId;
            this.value = value;
            this.rank = rank;
            this.nonce = nonce;
        }

        private static int order(final Draft left, final Draft right) {
            return VoteOrder.compare(
                left.raceId,
                left.rank,
                left.candidateId,
                right.raceId,
                right.rank,
                right.candidateId
            );
        }
    }

    private static final class Head {
        private final String previous;
        private final int index;

        private Head(final String previous, final int index) {
            this.previous = previous;
            this.index = index;
        }

        private Head advance(final String entryHash) {
            return new Head(entryHash, this.index + 1);
        }
    }
}
