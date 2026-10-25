package org.sure_vote.repository;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.BallotIssuance;
import org.sure_vote.entity.BallotStyle;
import org.sure_vote.entity.CanvassStatement;
import org.sure_vote.entity.ChainStatus;
import org.sure_vote.entity.HeldKey;
import org.sure_vote.entity.HeldKeyPurpose;
import org.sure_vote.entity.IssuanceStatus;
import org.sure_vote.entity.RestoreIncident;
import org.sure_vote.entity.TenantValidator;
import org.sure_vote.entity.Vote;
import org.sure_vote.entity.VoteChain;
import org.sure_vote.entity.VoteChainEntry;
import org.sure_vote.entity.VoteChainValidationResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemorySureVoteRepositoryTest {
    private InMemorySureVoteRepository repository;

    @BeforeEach
    void setUp() {
        this.repository = new InMemorySureVoteRepository();
    }

    @Test
    void rollsBackAFailedReplace() {
        final var original = this.inserted("abc");
        final var failure = assertThrows(IllegalStateException.class, () -> this.replaceAndFail(original));
        final var stored = this.repository.find(VoteChain.class, original.getId());
        final var chain = stored.orElseThrow();
        assertEquals("stop", failure.getMessage());
        assertEquals("abc", chain.getHash());
    }

    @Test
    void anInnerFailureKeepsTheOuterInsert() {
        final var outer = this.chain(UUID.randomUUID(), "outer");
        final var result = this.repository.transaction(() -> this.outerWork(outer));
        final var stored = this.repository.find(VoteChain.class, outer.getId());
        final var found = stored.orElseThrow();
        final var listed = this.repository.list(VoteChain.class);
        assertEquals("ok", result);
        assertEquals("outer", found.getHash());
        assertEquals(1, listed.size());
    }

    @Test
    void rejectsANullId() {
        final var failure = assertThrows(IllegalArgumentException.class, this::insertWithoutId);
        assertEquals("id is required", failure.getMessage());
    }

    @Test
    void rejectsADuplicateId() {
        final var chain = this.inserted("abc");
        final var failure = assertThrows(IllegalArgumentException.class, () -> this.insertAgain(chain));
        assertEquals("Duplicate id for VoteChain", failure.getMessage());
    }

    @Test
    void ballotIsInsertOnly() {
        final var failure = assertThrows(IllegalArgumentException.class, this::replaceBallot);
        assertEquals("Ballot is insert-only", failure.getMessage());
    }

    @Test
    void aRaceHasOneChain() {
        final var failure = assertThrows(IllegalArgumentException.class, this::twoChains);
        assertEquals("A race has one vote chain", failure.getMessage());
    }

    @Test
    void oneAcceptedIssuancePerVoter() {
        final var failure = assertThrows(IllegalArgumentException.class, this::twoIssuances);
        assertEquals("Voter already has an accepted issuance", failure.getMessage());
    }

    @Test
    void voterAccessHashIsUniqueAcrossElections() {
        final var failure = assertThrows(IllegalArgumentException.class, this::twoBallots);
        assertEquals("Duplicate voter access hash", failure.getMessage());
    }

    @Test
    void chainNonceIsUnique() {
        final var failure = assertThrows(IllegalArgumentException.class, this::twoNonces);
        assertEquals("Duplicate chain nonce", failure.getMessage());
    }

    @Test
    void listIsACopy() {
        final var chain = this.inserted("abc");
        final var listed = this.repository.list(VoteChain.class);
        assertEquals(1, listed.size());
        assertThrows(UnsupportedOperationException.class, () -> listed.add(chain));
    }

    @Test
    void findMissesAnUnknownId() {
        final var found = this.repository.find(VoteChain.class, UUID.randomUUID());
        assertTrue(found.isEmpty());
    }

    @Test
    void replaceRejectsAnUnknownId() {
        final var failure = assertThrows(IllegalArgumentException.class, this::replaceMissing);
        assertEquals("Unknown id for VoteChain", failure.getMessage());
    }

    @Test
    void aNullRowIsRejected() {
        final var failure = assertThrows(IllegalArgumentException.class, this::insertNull);
        assertEquals("row is required", failure.getMessage());
    }

    @Test
    void findRejectsANullId() {
        final var failure = assertThrows(IllegalArgumentException.class, this::findNull);
        assertEquals("id is required", failure.getMessage());
    }

    @Test
    void ledgerRowsAreInsertOnly() {
        this.assertInsertOnly(this::replaceVote, "Vote is insert-only");
        this.assertInsertOnly(this::replaceEntry, "VoteChainEntry is insert-only");
        this.assertInsertOnly(this::replaceCanvass, "CanvassStatement is insert-only");
        this.assertInsertOnly(this::replaceIncident, "RestoreIncident is insert-only");
        this.assertInsertOnly(this::replaceValidation, "VoteChainValidationResult is insert-only");
        this.assertInsertOnly(this::replaceKey, "HeldKey is insert-only");
    }

    @Test
    void aShowcaseKeyIsUnique() {
        final var failure = assertThrows(IllegalArgumentException.class, this::twoKeys);
        assertEquals("Duplicate showcase key", failure.getMessage());
    }

    @Test
    void aVoteHasOneChainEntry() {
        final var failure = assertThrows(IllegalArgumentException.class, this::twoEntriesForOneVote);
        assertEquals("Duplicate vote on a chain", failure.getMessage());
    }

    @Test
    void aChainIndexIsUnique() {
        final var failure = assertThrows(IllegalArgumentException.class, this::twoEntriesAtOneIndex);
        assertEquals("Duplicate chain index", failure.getMessage());
    }

    @Test
    void nonceMustBeOneToSixtyFourCharacters() {
        final var missing = assertThrows(IllegalArgumentException.class, () -> this.insertNonce(null));
        final var empty = assertThrows(IllegalArgumentException.class, () -> this.insertNonce(""));
        final var longNonce = "n".repeat(65);
        final var tooLong = assertThrows(IllegalArgumentException.class, () -> this.insertNonce(longNonce));
        assertEquals("nonce must be 1 to 64 characters", missing.getMessage());
        assertEquals("nonce must be 1 to 64 characters", empty.getMessage());
        assertEquals("nonce must be 1 to 64 characters", tooLong.getMessage());
    }

    @Test
    void aNonceOfSixtyFourCharactersIsAccepted() {
        final var listed = this.insertedNonce("n".repeat(64));
        assertEquals(1, listed.size());
    }

    @Test
    void eachRaceHasItsOwnChain() {
        final var listed = this.twoRaces();
        assertEquals(2, listed.size());
    }

    @Test
    void replacingAChainOntoATakenRaceIsRejected() {
        final var failure = assertThrows(IllegalArgumentException.class, this::moveChainOntoTakenRace);
        assertEquals("A race has one vote chain", failure.getMessage());
    }

    @Test
    void theSameAccessHashIsRejectedInAnotherElection() {
        final var failure = assertThrows(IllegalArgumentException.class, this::sameHashTwoElections);
        assertEquals("Duplicate voter access hash", failure.getMessage());
    }

    @Test
    void voterAccessHashIsRequired() {
        final var blank = assertThrows(IllegalArgumentException.class, this::blankHash);
        final var missing = assertThrows(IllegalArgumentException.class, this::missingHash);
        assertEquals("voterAccessHash is required", blank.getMessage());
        assertEquals("voterAccessHash is required", missing.getMessage());
    }

    @Test
    void ballotElectionIsRequired() {
        final var failure = assertThrows(IllegalArgumentException.class, this::ballotWithoutElection);
        assertEquals("electionId is required", failure.getMessage());
    }

    @Test
    void anElectionHasOneCanvassStatement() {
        final var failure = assertThrows(IllegalArgumentException.class, this::twoCanvasses);
        assertEquals("An election has one canvass statement", failure.getMessage());
    }

    @Test
    void anElectionHasOneRestoreIncident() {
        final var failure = assertThrows(IllegalArgumentException.class, this::twoIncidents);
        assertEquals("An election has one restore incident", failure.getMessage());
    }

    @Test
    void aBallotStyleRowIsUnique() {
        final var failure = assertThrows(IllegalArgumentException.class, this::twoStyles);
        assertEquals("Duplicate ballot style row", failure.getMessage());
    }

    @Test
    void aTenantValidatorIsUnique() {
        final var failure = assertThrows(IllegalArgumentException.class, this::twoValidators);
        assertEquals("Duplicate tenant validator", failure.getMessage());
    }

    @Test
    void aVoidedIssuanceDoesNotUseTheAcceptedSlot() {
        final var listed = this.voidedThenAccepted();
        assertEquals(2, listed.size());
    }

    private String outerWork(final VoteChain outer) {
        this.repository.insert(outer);
        this.catchInner();
        return "ok";
    }

    private void catchInner() {
        final var inner = this.chain(UUID.randomUUID(), "inner");
        try {
            this.repository.transaction(() -> this.insertAndThrow(inner));
        }
        catch (final IllegalStateException failure) {
            return;
        }
    }

    private VoteChain insertAndThrow(final VoteChain chain) {
        this.repository.insert(chain);
        throw new IllegalStateException("inner");
    }

    private void replaceAndFail(final VoteChain original) {
        final var next = this.copy(original, "def");
        this.repository.transaction(() -> this.replaceThenThrow(next));
    }

    private VoteChain replaceThenThrow(final VoteChain next) {
        this.repository.replace(next);
        throw new IllegalStateException("stop");
    }

    private void insertWithoutId() {
        final var chain = new VoteChain();
        chain.setRaceId(UUID.randomUUID());
        this.repository.insert(chain);
    }

    private void insertAgain(final VoteChain chain) {
        final var again = new VoteChain();
        again.setId(chain.getId());
        again.setRaceId(UUID.randomUUID());
        again.setStatus(ChainStatus.ACTIVE);
        again.setHash("def");
        this.repository.insert(again);
    }

    private void replaceBallot() {
        final var ballot = new Ballot();
        ballot.setId(UUID.randomUUID());
        this.repository.replace(ballot);
    }

    private void twoChains() {
        final var raceId = UUID.randomUUID();
        final var first = this.chain(raceId, "a");
        final var second = this.chain(raceId, "b");
        this.repository.insert(first);
        this.repository.insert(second);
    }

    private void twoIssuances() {
        final var personId = UUID.randomUUID();
        final var electionId = UUID.randomUUID();
        final var first = this.issuance(personId, electionId);
        final var second = this.issuance(personId, electionId);
        this.repository.insert(first);
        this.repository.insert(second);
    }

    private void twoBallots() {
        final var electionId = UUID.randomUUID();
        final var first = this.ballot(electionId, "ab");
        final var second = this.ballot(electionId, "ab");
        this.repository.insert(first);
        this.repository.insert(second);
    }

    private void twoNonces() {
        final var chainId = UUID.randomUUID();
        final var first = this.entry(chainId, UUID.randomUUID(), 0, "n-1");
        final var second = this.entry(chainId, UUID.randomUUID(), 1, "n-1");
        this.repository.insert(first);
        this.repository.insert(second);
    }

    private VoteChain inserted(final String hash) {
        final var chain = this.chain(UUID.randomUUID(), hash);
        this.repository.insert(chain);
        return chain;
    }

    private VoteChain chain(final UUID raceId, final String hash) {
        final var chain = new VoteChain();
        chain.setId(UUID.randomUUID());
        chain.setTenantId(UUID.randomUUID());
        chain.setRaceId(raceId);
        chain.setStatus(ChainStatus.ACTIVE);
        chain.setLength(0);
        chain.setHash(hash);
        return chain;
    }

    private VoteChain copy(final VoteChain original, final String hash) {
        final var next = new VoteChain();
        next.setId(original.getId());
        next.setTenantId(original.getTenantId());
        next.setRaceId(original.getRaceId());
        next.setStatus(ChainStatus.ACTIVE);
        next.setLength(0);
        next.setHash(hash);
        return next;
    }

    private BallotIssuance issuance(final UUID personId, final UUID electionId) {
        final var row = new BallotIssuance();
        row.setId(UUID.randomUUID());
        row.setTenantId(UUID.randomUUID());
        row.setPersonId(personId);
        row.setElectionId(electionId);
        row.setStatus(IssuanceStatus.ACCEPTED);
        return row;
    }

    private Ballot ballot(final UUID electionId, final String hash) {
        final var ballot = new Ballot();
        ballot.setId(UUID.randomUUID());
        ballot.setTenantId(UUID.randomUUID());
        ballot.setElectionId(electionId);
        ballot.setVoterAccessHash(hash);
        return ballot;
    }

    private VoteChainEntry entry(final UUID chainId, final UUID voteId, final int index, final String nonce) {
        final var entry = new VoteChainEntry();
        entry.setId(UUID.randomUUID());
        entry.setTenantId(UUID.randomUUID());
        entry.setVoteChainId(chainId);
        entry.setVoteId(voteId);
        entry.setIndex(index);
        entry.setNonce(nonce);
        return entry;
    }

    private void assertInsertOnly(final Executable work, final String message) {
        final var failure = assertThrows(IllegalArgumentException.class, work);
        assertEquals(message, failure.getMessage());
    }

    private void replaceMissing() {
        final var chain = this.chain(UUID.randomUUID(), "abc");
        this.repository.replace(chain);
    }

    private void insertNull() {
        this.repository.insert((VoteChain) null);
    }

    private void findNull() {
        this.repository.find(VoteChain.class, null);
    }

    private void replaceVote() {
        final var vote = new Vote();
        vote.setId(UUID.randomUUID());
        this.repository.replace(vote);
    }

    private void replaceEntry() {
        final var entry = new VoteChainEntry();
        entry.setId(UUID.randomUUID());
        this.repository.replace(entry);
    }

    private void replaceCanvass() {
        final var statement = new CanvassStatement();
        statement.setId(UUID.randomUUID());
        this.repository.replace(statement);
    }

    private void replaceIncident() {
        final var incident = new RestoreIncident();
        incident.setId(UUID.randomUUID());
        this.repository.replace(incident);
    }

    private void replaceKey() {
        final var key = new HeldKey();
        key.setId(UUID.randomUUID());
        this.repository.replace(key);
    }

    private void twoKeys() {
        final var subject = UUID.randomUUID();
        final var first = this.held(subject);
        final var second = this.held(subject);
        this.repository.insert(first);
        this.repository.insert(second);
    }

    private HeldKey held(final UUID subject) {
        final var key = new HeldKey();
        key.setId(UUID.randomUUID());
        key.setTenantId(UUID.randomUUID());
        key.setPurpose(HeldKeyPurpose.OPERATOR);
        key.setSubjectId(subject);
        key.setSeed("ab".repeat(32));
        return key;
    }

    private void replaceValidation() {
        final var result = new VoteChainValidationResult();
        result.setId(UUID.randomUUID());
        this.repository.replace(result);
    }

    private void twoEntriesForOneVote() {
        final var chainId = UUID.randomUUID();
        final var voteId = UUID.randomUUID();
        final var first = this.entry(chainId, voteId, 0, "n-1");
        final var second = this.entry(chainId, voteId, 1, "n-2");
        this.repository.insert(first);
        this.repository.insert(second);
    }

    private void twoEntriesAtOneIndex() {
        final var chainId = UUID.randomUUID();
        final var first = this.entry(chainId, UUID.randomUUID(), 0, "n-1");
        final var second = this.entry(chainId, UUID.randomUUID(), 0, "n-2");
        this.repository.insert(first);
        this.repository.insert(second);
    }

    private void insertNonce(final String nonce) {
        final var entry = this.entry(UUID.randomUUID(), UUID.randomUUID(), 0, "n-1");
        entry.setNonce(nonce);
        this.repository.insert(entry);
    }

    private List<VoteChainEntry> insertedNonce(final String nonce) {
        this.insertNonce(nonce);
        return this.repository.list(VoteChainEntry.class);
    }

    private List<VoteChain> twoRaces() {
        final var first = this.chain(UUID.randomUUID(), "a");
        final var second = this.chain(UUID.randomUUID(), "b");
        this.repository.insert(first);
        this.repository.insert(second);
        return this.repository.list(VoteChain.class);
    }

    private void moveChainOntoTakenRace() {
        final var taken = UUID.randomUUID();
        final var first = this.chain(UUID.randomUUID(), "a");
        final var second = this.chain(taken, "b");
        final var moved = this.copy(first, "a");
        this.repository.insert(first);
        this.repository.insert(second);
        moved.setRaceId(taken);
        this.repository.replace(moved);
    }

    private void sameHashTwoElections() {
        final var first = this.ballot(UUID.randomUUID(), "ab");
        final var second = this.ballot(UUID.randomUUID(), "ab");
        this.repository.insert(first);
        this.repository.insert(second);
    }

    private void blankHash() {
        final var ballot = this.ballot(UUID.randomUUID(), " ");
        this.repository.insert(ballot);
    }

    private void missingHash() {
        final var ballot = this.ballot(UUID.randomUUID(), "ab");
        ballot.setVoterAccessHash(null);
        this.repository.insert(ballot);
    }

    private void ballotWithoutElection() {
        final var ballot = this.ballot(UUID.randomUUID(), "ab");
        ballot.setElectionId(null);
        this.repository.insert(ballot);
    }

    private void twoCanvasses() {
        final var electionId = UUID.randomUUID();
        final var first = this.canvass(electionId);
        final var second = this.canvass(electionId);
        this.repository.insert(first);
        this.repository.insert(second);
    }

    private void twoIncidents() {
        final var electionId = UUID.randomUUID();
        final var first = this.incident(electionId);
        final var second = this.incident(electionId);
        this.repository.insert(first);
        this.repository.insert(second);
    }

    private void twoStyles() {
        final var electionId = UUID.randomUUID();
        final var precinctId = UUID.randomUUID();
        final var raceId = UUID.randomUUID();
        final var first = this.style(electionId, precinctId, raceId);
        final var second = this.style(electionId, precinctId, raceId);
        this.repository.insert(first);
        this.repository.insert(second);
    }

    private void twoValidators() {
        final var tenantId = UUID.randomUUID();
        final var validatorId = UUID.randomUUID();
        final var first = this.validator(tenantId, validatorId);
        final var second = this.validator(tenantId, validatorId);
        this.repository.insert(first);
        this.repository.insert(second);
    }

    private List<BallotIssuance> voidedThenAccepted() {
        final var personId = UUID.randomUUID();
        final var electionId = UUID.randomUUID();
        final var voided = this.issuance(personId, electionId);
        final var accepted = this.issuance(personId, electionId);
        voided.setStatus(IssuanceStatus.VOIDED);
        this.repository.insert(voided);
        this.repository.insert(accepted);
        return this.repository.list(BallotIssuance.class);
    }

    private CanvassStatement canvass(final UUID electionId) {
        final var statement = new CanvassStatement();
        statement.setId(UUID.randomUUID());
        statement.setTenantId(UUID.randomUUID());
        statement.setElectionId(electionId);
        return statement;
    }

    private RestoreIncident incident(final UUID electionId) {
        final var incident = new RestoreIncident();
        incident.setId(UUID.randomUUID());
        incident.setTenantId(UUID.randomUUID());
        incident.setElectionId(electionId);
        return incident;
    }

    private BallotStyle style(final UUID electionId, final UUID precinctId, final UUID raceId) {
        final var style = new BallotStyle();
        style.setId(UUID.randomUUID());
        style.setTenantId(UUID.randomUUID());
        style.setElectionId(electionId);
        style.setVotingPrecinctId(precinctId);
        style.setRaceId(raceId);
        return style;
    }

    private TenantValidator validator(final UUID tenantId, final UUID validatorId) {
        final var link = new TenantValidator();
        link.setId(UUID.randomUUID());
        link.setTenantId(tenantId);
        link.setValidatorId(validatorId);
        return link;
    }
}
