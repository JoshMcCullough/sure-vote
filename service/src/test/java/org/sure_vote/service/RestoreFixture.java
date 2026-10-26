package org.sure_vote.service;

import java.time.Instant;
import java.util.UUID;

import org.sure_vote.entity.ValidationResultStatus;
import org.sure_vote.entity.VoteChain;
import org.sure_vote.entity.VoteChainValidationResult;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RestoreFixture {
    static final Instant WHEN = Instant.parse("2026-11-06T09:30:00.250Z");
    static final String LOST_HASH = "cd".repeat(32);

    private RestoreFixture() {
    }

    static VoteChain restoredAfterLoss(final SampleElection world) {
        final var chain = castAndWitness(world);
        witnessLostHead(world, chain);
        return chain;
    }

    static VoteChain castAndWitness(final SampleElection world) {
        final var cast = world.castOne(world.slotA, "Y");
        final var chain = chain(world);
        witness(world, chain);
        assertEquals(world.electionId, cast.getElectionId());
        return chain(world);
    }

    static void witness(final SampleElection world, final VoteChain chain) {
        final var payload = Payloads.validation(
            chain.getId(),
            world.validatorId,
            chain.getHash(),
            chain.getLength(),
            "valid",
            WHEN,
            null,
            null
        );
        final var raw = Digests.unhex(Digests.sha256Hex(payload));
        final var seed = world.validatorKey.seed();
        final var signature = Signatures.sign(seed, raw);
        final var request = new ValidationRequest(
            chain.getId(),
            world.validatorId,
            chain.getHash(),
            chain.getLength(),
            ValidationResultStatus.VALID,
            WHEN,
            "crypto",
            null,
            null,
            signature
        );
        world.app.fileValidation(world.electionId, request, world.actor);
    }

    static void witnessLostHead(final SampleElection world, final VoteChain chain) {
        final var row = new VoteChainValidationResult();
        row.setId(UUID.randomUUID());
        row.setTenantId(chain.getTenantId());
        row.setCreatedAt(WHEN);
        row.setUpdatedAt(WHEN);
        row.setCreatedBy(world.actor);
        row.setUpdatedBy(world.actor);
        row.setVersion(0);
        row.setVoteChainId(chain.getId());
        row.setValidatorId(world.validatorId);
        row.setDateValidated(WHEN.plusSeconds(60));
        row.setValidatedThroughHash(LOST_HASH);
        row.setLength(chain.getLength() + 1);
        row.setMethod("crypto");
        row.setResult(ValidationResultStatus.VALID);
        row.setPayload("{}");
        row.setHash("ef".repeat(32));
        row.setSignature(Signatures.ID + ":" + "00".repeat(64));
        world.repository.insert(row);
    }

    static VoteChain chain(final SampleElection world) {
        final var rows = world.repository.list(VoteChain.class);
        for (final var row : rows) {
            if (world.raceId.equals(row.getRaceId())) {
                return row;
            }
        }
        throw new IllegalStateException("missing chain");
    }
}
