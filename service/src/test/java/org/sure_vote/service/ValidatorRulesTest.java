package org.sure_vote.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.sure_vote.entity.Outcome;
import org.sure_vote.entity.TenantValidator;
import org.sure_vote.entity.ValidationResultStatus;
import org.sure_vote.entity.VoteChain;
import org.sure_vote.entity.VoteChainValidationResult;
import org.sure_vote.entity.VoteChainValidator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidatorRulesTest {
    private static final Instant WHEN = Instant.parse("2026-11-05T12:00:00.000Z");

    @Test
    void aProductionElectionNeedsAnOutsideValidator() {
        final var world = SampleElection.upcomingFptp();
        final var failure = this.activateWithoutValidator(world);
        assertEquals("a production election needs an outside validator", failure.getMessage());
    }

    @Test
    void headsStayPrivateUntilTheElectionIsActive() {
        final var world = SampleElection.upcomingFptp();
        final var failure = assertThrows(ConstraintException.class, () -> world.app.heads(world.electionId));
        assertEquals("election is not public yet", failure.getMessage());
    }

    @Test
    void anEmptyChainIsNotAWitnessGap() {
        final var world = SampleElection.fptp();
        final var open = this.agedGap(world);
        assertFalse(open);
    }

    @Test
    void aChangedHeadIsUnwitnessedAfterADay() {
        final var world = SampleElection.fptp();
        final var before = this.freshGap(world);
        final var late = this.lateGap(world);
        final var filed = this.file(world, ValidationResultStatus.VALID);
        final var after = this.gap(world);
        assertFalse(before);
        assertTrue(late);
        assertFalse(after);
        assertEquals(ValidationResultStatus.VALID, filed.getResult());
    }

    @Test
    void anInvalidResultDoesNotChangeTheTally() {
        final var world = SampleElection.fptp();
        final var filed = this.fileAfterCast(world);
        final var notices = this.closedHeads(world);
        final var notice = notices.getFirst();
        world.assertOutcome(world.slotA, Outcome.WON, 1, 1);
        assertEquals(ValidationResultStatus.INVALID, filed.getResult());
        assertNotNull(notice.canvassHash());
        assertNotNull(notice.signature());
        assertFalse(notice.witnessGap());
    }

    @Test
    void aSignatureOverAMissingHeadIsRejected() {
        final var world = SampleElection.fptp();
        final var failure = assertThrows(ConstraintException.class, () -> this.lie(world));
        assertEquals("validatedThroughHash is not on the chain", failure.getMessage());
    }

    @Test
    void aValidatorSignatureMustMatch() {
        final var world = SampleElection.fptp();
        final var failure = assertThrows(ConstraintException.class, () -> this.badSignature(world));
        assertEquals("Validator signature does not match the validation result", failure.getMessage());
    }

    @Test
    void anUnregisteredValidatorCannotFile() {
        final var world = SampleElection.fptp();
        final var failure = assertThrows(ConstraintException.class, () -> this.outsider(world));
        assertEquals("validator is not registered for this tenant", failure.getMessage());
    }

    @Test
    void aValidatorNeedsANameAndAPublicKey() {
        final var world = SampleElection.upcomingFptp();
        final var blank = assertThrows(ConstraintException.class, () -> this.blankName(world));
        final var key = assertThrows(ConstraintException.class, () -> this.badKey(world));
        final var saved = this.blankDescription(world);
        assertEquals("validator name is required", blank.getMessage());
        assertEquals("validator public key", key.getMessage());
        assertNull(saved.getDescription());
    }

    @Test
    void evidenceMustBeASha256Hex() {
        final var world = SampleElection.fptp();
        final var failure = assertThrows(ConstraintException.class, () -> this.badEvidence(world));
        assertEquals("evidenceHash must be lowercase SHA-256 hex", failure.getMessage());
    }

    private boolean freshGap(final SampleElection world) {
        world.castOne(world.slotA, "Y");
        return this.gap(world);
    }

    private boolean lateGap(final SampleElection world) {
        this.age(world);
        return this.gap(world);
    }

    private ConstraintException activateWithoutValidator(final SampleElection world) {
        this.dropValidators(world);
        return assertThrows(ConstraintException.class, () -> world.activate());
    }

    private boolean agedGap(final SampleElection world) {
        this.age(world);
        return this.gap(world);
    }

    private boolean gap(final SampleElection world) {
        final var notices = world.app.heads(world.electionId);
        final var notice = notices.getFirst();
        return notice.witnessGap();
    }

    private VoteChainValidationResult fileAfterCast(final SampleElection world) {
        world.castOne(world.slotA, "Y");
        return this.file(world, ValidationResultStatus.INVALID);
    }

    private List<HeadNotice> closedHeads(final SampleElection world) {
        world.close();
        return world.app.heads(world.electionId);
    }

    private void dropValidators(final SampleElection world) {
        final var links = world.repository.list(TenantValidator.class);
        for (final var link : links) {
            link.setDeletedAt(Instant.parse("2026-11-01T00:00:00Z"));
            world.repository.replace(link);
        }
    }

    private void age(final SampleElection world) {
        final var chain = this.chain(world);
        final var now = Instant.now();
        final var observed = now.minus(Duration.ofHours(25));
        final var next = Copies.chain(chain);
        next.setUpdatedAt(observed);
        world.repository.replace(next);
    }

    private void lie(final SampleElection world) {
        final var chain = this.chain(world);
        final var hash = "ab".repeat(32);
        final var length = chain.getLength();
        final var signature = this.sign(world.validatorKey, chain, world.validatorId, hash, length, "valid", null);
        this.submit(world, chain, world.validatorId, hash, length, ValidationResultStatus.VALID, null, signature);
    }

    private void badSignature(final SampleElection world) {
        final var chain = this.chain(world);
        final var signature = "aa".repeat(64);
        final var hash = chain.getHash();
        final var length = chain.getLength();
        this.submit(world, chain, world.validatorId, hash, length, ValidationResultStatus.VALID, null, signature);
    }

    private void badEvidence(final SampleElection world) {
        final var chain = this.chain(world);
        final var hash = chain.getHash();
        final var length = chain.getLength();
        final var signature = this.sign(world.validatorKey, chain, world.validatorId, hash, length, "valid", "zz");
        this.submit(world, chain, world.validatorId, hash, length, ValidationResultStatus.VALID, "zz", signature);
    }

    private void outsider(final SampleElection world) {
        final var key = Signatures.generate();
        final var saved = this.otherValidator(world, key);
        final var chain = this.chain(world);
        final var hash = chain.getHash();
        final var length = chain.getLength();
        final var validatorId = saved.getId();
        final var signature = this.sign(key, chain, validatorId, hash, length, "valid", null);
        this.submit(world, chain, validatorId, hash, length, ValidationResultStatus.VALID, null, signature);
    }

    private VoteChainValidator otherValidator(final SampleElection world, final SigningKey key) {
        final var validator = new VoteChainValidator();
        final var publicKey = key.publicKey();
        validator.setName("Other");
        validator.setPublicKey(publicKey);
        return world.app.registerValidator(validator, world.actor);
    }

    private void blankName(final SampleElection world) {
        final var validator = new VoteChainValidator();
        final var key = Signatures.generate();
        final var publicKey = key.publicKey();
        validator.setName(" ");
        validator.setPublicKey(publicKey);
        world.app.registerValidator(validator, world.actor);
    }

    private void badKey(final SampleElection world) {
        final var validator = new VoteChainValidator();
        validator.setName("Witness");
        validator.setPublicKey("zz");
        world.app.registerValidator(validator, world.actor);
    }

    private VoteChainValidator blankDescription(final SampleElection world) {
        final var validator = new VoteChainValidator();
        final var key = Signatures.generate();
        final var publicKey = key.publicKey();
        validator.setName("Witness");
        validator.setDescription(" ");
        validator.setPublicKey(publicKey);
        return world.app.registerValidator(validator, world.actor);
    }

    private VoteChainValidationResult file(final SampleElection world, final ValidationResultStatus status) {
        final var chain = this.chain(world);
        final var hash = chain.getHash();
        final var length = chain.getLength();
        final var wire = status.wire();
        final var signature = this.sign(world.validatorKey, chain, world.validatorId, hash, length, wire, null);
        return this.submit(world, chain, world.validatorId, hash, length, status, null, signature);
    }

    private VoteChainValidationResult submit(
        final SampleElection world,
        final VoteChain chain,
        final UUID validatorId,
        final String hash,
        final int length,
        final ValidationResultStatus status,
        final String evidence,
        final String signature
    ) {
        final var request = new ValidationRequest(
            chain.getId(),
            validatorId,
            hash,
            length,
            status,
            WHEN,
            "crypto",
            null,
            evidence,
            signature
        );
        return world.app.fileValidation(world.electionId, request, world.actor);
    }

    private String sign(
        final SigningKey key,
        final VoteChain chain,
        final UUID validatorId,
        final String hash,
        final int length,
        final String status,
        final String evidence
    ) {
        final var payload = Payloads.validation(
            chain.getId(),
            validatorId,
            hash,
            length,
            status,
            WHEN,
            null,
            evidence
        );
        final var digest = Digests.sha256Hex(payload);
        final var raw = Digests.unhex(digest);
        final var seed = key.seed();
        return Signatures.sign(seed, raw);
    }

    private VoteChain chain(final SampleElection world) {
        final var rows = world.repository.list(VoteChain.class);
        for (final var row : rows) {
            if (world.raceId.equals(row.getRaceId())) {
                return row;
            }
        }
        throw new IllegalStateException("missing chain");
    }
}
