package org.sure_vote.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.sure_vote.entity.Address;
import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.BallotIssuance;
import org.sure_vote.entity.BallotStyle;
import org.sure_vote.entity.CanvassStatement;
import org.sure_vote.entity.ChainStatus;
import org.sure_vote.entity.IssuanceStatus;
import org.sure_vote.entity.Outcome;
import org.sure_vote.entity.Person;
import org.sure_vote.entity.RaceCandidate;
import org.sure_vote.entity.RaceCandidateStatus;
import org.sure_vote.entity.RestoreIncident;
import org.sure_vote.entity.TenantValidator;
import org.sure_vote.entity.Vote;
import org.sure_vote.entity.VoteChain;
import org.sure_vote.entity.VoteChainEntry;
import org.sure_vote.entity.VoteChainValidationResult;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresRepositoryTest {
    private static final Instant STAMP = Instant.parse("2026-11-03T12:00:00.123456Z");

    private static PostgreSQLContainer<?> postgres;
    private static JdbcSureVoteRepository repository;

    private ParentRows rows;
    private UUID entrySlot;

    @BeforeAll
    static void start() {
        final var container = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres = container;
        container.start();
        PostgresRepositoryTest.open(container);
    }

    @AfterAll
    static void stop() {
        final var container = postgres;
        if (container != null) {
            container.stop();
        }
    }

    @BeforeEach
    void setUp() {
        repository.clear();
        this.rows = new ParentRows(repository);
    }

    @Test
    void rollsBackAFailedReplace() {
        final var original = this.inserted("abc");
        final var failure = assertThrows(IllegalStateException.class, () -> this.replaceAndFail(original));
        final var stored = repository.find(VoteChain.class, original.getId());
        final var chain = stored.orElseThrow();
        assertEquals("stop", failure.getMessage());
        assertEquals("abc", chain.getHash());
    }

    @Test
    void anInnerFailureKeepsTheOuterInsert() {
        final var outer = this.chain(this.rows.race(), "outer");
        final var result = repository.transaction(() -> this.outerWork(outer));
        final var stored = repository.find(VoteChain.class, outer.getId());
        final var found = stored.orElseThrow();
        final var listed = repository.list(VoteChain.class);
        assertEquals("ok", result);
        assertEquals("outer", found.getHash());
        assertEquals(1, listed.size());
    }

    @Test
    void anOuterFailureRollsBackANestedInsert() {
        final var chain = this.chain(this.rows.race(), "nested");
        final var failure = assertThrows(IllegalStateException.class, () -> this.nestedThenStop(chain));
        final var listed = repository.list(VoteChain.class);
        assertEquals("stop", failure.getMessage());
        assertEquals(0, listed.size());
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
        final var listed = repository.list(VoteChain.class);
        assertEquals(1, listed.size());
        assertThrows(UnsupportedOperationException.class, () -> listed.add(chain));
    }

    @Test
    void findMissesAnUnknownId() {
        final var found = repository.find(VoteChain.class, UUID.randomUUID());
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

    @Test
    void replaceStoresTheNewChain() {
        final var original = this.inserted("abc");
        final var next = this.copy(original, "def");
        this.replaceChain(next);
        this.assertReplaced(original.getId());
    }

    @Test
    void aMissingTimestampStaysNull() {
        final var chain = this.chain(this.rows.race(), "abc");
        this.insertWithoutStatus(chain);
        this.assertNullChain(chain.getId());
    }

    @Test
    void listOrdersByCreatedAtThenId() {
        final var earlier = Instant.parse("2026-01-01T00:00:00Z");
        final var later = Instant.parse("2026-01-02T00:00:00Z");
        final var first = this.chain(this.rows.race(), "later");
        final var second = this.chain(this.rows.race(), "earlier");
        this.insertOrdered(first, second, later, earlier);
        this.assertOldest(second.getId());
    }

    @Test
    void roundTripsColumnTypes() {
        final var address = this.address();
        final var person = this.person();
        final var slot = this.slot();
        repository.insert(address);
        repository.insert(person);
        repository.insert(slot);
        address.setStreet1("2 Main");
        address.setLatitude(Double.valueOf(42.5));
        repository.replace(address);
        this.assertAddress(address.getId());
        this.assertPerson(person.getId());
        this.assertSlot(slot.getId());
    }

    private static void open(final PostgreSQLContainer<?> container) {
        final var url = container.getJdbcUrl();
        final var user = container.getUsername();
        final var password = container.getPassword();
        JdbcSureVoteRepository.migrate(url, user, password);
        repository = JdbcSureVoteRepository.open(url, user, password);
    }

    private String outerWork(final VoteChain outer) {
        repository.insert(outer);
        this.catchInner();
        return "ok";
    }

    private void catchInner() {
        final var inner = this.chain(this.rows.race(), "inner");
        try {
            repository.transaction(() -> this.insertAndThrow(inner));
        }
        catch (final IllegalStateException failure) {
            return;
        }
    }

    private VoteChain insertAndThrow(final VoteChain chain) {
        repository.insert(chain);
        throw new IllegalStateException("inner");
    }

    private void nestedThenStop(final VoteChain chain) {
        repository.transaction(() -> this.insertNested(chain));
    }

    private String insertNested(final VoteChain chain) {
        repository.transaction(() -> this.insertChain(chain));
        throw new IllegalStateException("stop");
    }

    private VoteChain insertChain(final VoteChain chain) {
        repository.insert(chain);
        return chain;
    }

    private void replaceAndFail(final VoteChain original) {
        final var next = this.copy(original, "def");
        repository.transaction(() -> this.replaceThenThrow(next));
    }

    private VoteChain replaceThenThrow(final VoteChain next) {
        repository.replace(next);
        throw new IllegalStateException("stop");
    }

    private void insertWithoutId() {
        final var chain = new VoteChain();
        chain.setRaceId(UUID.randomUUID());
        repository.insert(chain);
    }

    private void insertAgain(final VoteChain chain) {
        final var again = new VoteChain();
        again.setId(chain.getId());
        again.setRaceId(UUID.randomUUID());
        again.setStatus(ChainStatus.ACTIVE);
        again.setHash("def");
        repository.insert(again);
    }

    private void replaceBallot() {
        final var ballot = new Ballot();
        ballot.setId(UUID.randomUUID());
        repository.replace(ballot);
    }

    private void twoChains() {
        final var raceId = this.rows.race();
        final var first = this.chain(raceId, "a");
        final var second = this.chain(raceId, "b");
        repository.insert(first);
        repository.insert(second);
    }

    private void twoIssuances() {
        final var personId = this.rows.personId;
        final var electionId = this.rows.electionId;
        final var first = this.issuance(personId, electionId);
        final var second = this.issuance(personId, electionId);
        repository.insert(first);
        repository.insert(second);
    }

    private void twoBallots() {
        final var electionId = this.rows.electionId;
        final var first = this.ballot(electionId, "ab");
        final var second = this.ballot(electionId, "ab");
        repository.insert(first);
        repository.insert(second);
    }

    private void twoNonces() {
        final var chainId = this.entryChain();
        final var first = this.entry(chainId, this.rows.vote(this.entrySlot), 0, "n-1");
        final var second = this.entry(chainId, this.rows.vote(this.entrySlot), 1, "n-1");
        repository.insert(first);
        repository.insert(second);
    }

    private UUID entryChain() {
        final var raceId = this.rows.race();
        final var chain = this.chain(raceId, "e0");
        this.entrySlot = this.rows.slot(raceId);
        repository.insert(chain);
        return chain.getId();
    }

    private VoteChain inserted(final String hash) {
        final var chain = this.chain(this.rows.race(), hash);
        repository.insert(chain);
        return chain;
    }

    private VoteChain chain(final UUID raceId, final String hash) {
        final var chain = new VoteChain();
        chain.setId(UUID.randomUUID());
        chain.setTenantId(this.rows.tenantId);
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
        row.setTenantId(this.rows.tenantId);
        row.setPersonId(personId);
        row.setElectionId(electionId);
        row.setStatus(IssuanceStatus.ACCEPTED);
        return row;
    }

    private Ballot ballot(final UUID electionId, final String hash) {
        final var ballot = new Ballot();
        ballot.setId(UUID.randomUUID());
        ballot.setTenantId(this.rows.tenantId);
        ballot.setElectionId(electionId);
        ballot.setVotingPrecinctId(this.rows.precinctId);
        ballot.setVoterAccessHash(hash);
        return ballot;
    }

    private VoteChainEntry entry(final UUID chainId, final UUID voteId, final int index, final String nonce) {
        final var entry = new VoteChainEntry();
        entry.setId(UUID.randomUUID());
        entry.setTenantId(this.rows.tenantId);
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
        final var chain = this.chain(this.rows.race(), "abc");
        repository.replace(chain);
    }

    private void insertNull() {
        repository.insert((VoteChain) null);
    }

    private void findNull() {
        repository.find(VoteChain.class, null);
    }

    private void replaceVote() {
        final var vote = new Vote();
        vote.setId(UUID.randomUUID());
        repository.replace(vote);
    }

    private void replaceEntry() {
        final var entry = new VoteChainEntry();
        entry.setId(UUID.randomUUID());
        repository.replace(entry);
    }

    private void replaceCanvass() {
        final var statement = new CanvassStatement();
        statement.setId(UUID.randomUUID());
        repository.replace(statement);
    }

    private void replaceIncident() {
        final var incident = new RestoreIncident();
        incident.setId(UUID.randomUUID());
        repository.replace(incident);
    }

    private void replaceValidation() {
        final var result = new VoteChainValidationResult();
        result.setId(UUID.randomUUID());
        repository.replace(result);
    }

    private void twoEntriesForOneVote() {
        final var chainId = this.entryChain();
        final var voteId = this.rows.vote(this.entrySlot);
        final var first = this.entry(chainId, voteId, 0, "n-1");
        final var second = this.entry(chainId, voteId, 1, "n-2");
        repository.insert(first);
        repository.insert(second);
    }

    private void twoEntriesAtOneIndex() {
        final var chainId = this.entryChain();
        final var first = this.entry(chainId, this.rows.vote(this.entrySlot), 0, "n-1");
        final var second = this.entry(chainId, this.rows.vote(this.entrySlot), 0, "n-2");
        repository.insert(first);
        repository.insert(second);
    }

    private void insertNonce(final String nonce) {
        final var chainId = this.entryChain();
        final var entry = this.entry(chainId, this.rows.vote(this.entrySlot), 0, "n-1");
        entry.setNonce(nonce);
        repository.insert(entry);
    }

    private List<VoteChainEntry> insertedNonce(final String nonce) {
        this.insertNonce(nonce);
        return repository.list(VoteChainEntry.class);
    }

    private List<VoteChain> twoRaces() {
        final var first = this.chain(this.rows.race(), "a");
        final var second = this.chain(this.rows.race(), "b");
        repository.insert(first);
        repository.insert(second);
        return repository.list(VoteChain.class);
    }

    private void moveChainOntoTakenRace() {
        final var taken = this.rows.race();
        final var first = this.chain(this.rows.race(), "a");
        final var second = this.chain(taken, "b");
        final var moved = this.copy(first, "a");
        repository.insert(first);
        repository.insert(second);
        moved.setRaceId(taken);
        repository.replace(moved);
    }

    private void sameHashTwoElections() {
        final var first = this.ballot(this.rows.electionId, "ab");
        final var second = this.ballot(this.rows.election(), "ab");
        repository.insert(first);
        repository.insert(second);
    }

    private void blankHash() {
        final var ballot = this.ballot(this.rows.electionId, " ");
        repository.insert(ballot);
    }

    private void missingHash() {
        final var ballot = this.ballot(this.rows.electionId, "ab");
        ballot.setVoterAccessHash(null);
        repository.insert(ballot);
    }

    private void ballotWithoutElection() {
        final var ballot = this.ballot(this.rows.electionId, "ab");
        ballot.setElectionId(null);
        repository.insert(ballot);
    }

    private void twoCanvasses() {
        final var electionId = this.rows.electionId;
        final var first = this.canvass(electionId);
        final var second = this.canvass(electionId);
        repository.insert(first);
        repository.insert(second);
    }

    private void twoIncidents() {
        final var electionId = this.rows.electionId;
        final var first = this.incident(electionId);
        final var second = this.incident(electionId);
        repository.insert(first);
        repository.insert(second);
    }

    private void twoStyles() {
        final var electionId = this.rows.electionId;
        final var precinctId = this.rows.precinctId;
        final var raceId = this.rows.race();
        final var first = this.style(electionId, precinctId, raceId);
        final var second = this.style(electionId, precinctId, raceId);
        repository.insert(first);
        repository.insert(second);
    }

    private void twoValidators() {
        final var tenantId = this.rows.tenantId;
        final var validatorId = this.rows.validator();
        final var first = this.validator(tenantId, validatorId);
        final var second = this.validator(tenantId, validatorId);
        repository.insert(first);
        repository.insert(second);
    }

    private List<BallotIssuance> voidedThenAccepted() {
        final var personId = this.rows.personId;
        final var electionId = this.rows.electionId;
        final var voided = this.issuance(personId, electionId);
        final var accepted = this.issuance(personId, electionId);
        voided.setStatus(IssuanceStatus.VOIDED);
        repository.insert(voided);
        repository.insert(accepted);
        return repository.list(BallotIssuance.class);
    }

    private CanvassStatement canvass(final UUID electionId) {
        final var statement = new CanvassStatement();
        statement.setId(UUID.randomUUID());
        statement.setTenantId(this.rows.tenantId);
        statement.setElectionId(electionId);
        return statement;
    }

    private RestoreIncident incident(final UUID electionId) {
        final var incident = new RestoreIncident();
        incident.setId(UUID.randomUUID());
        incident.setTenantId(this.rows.tenantId);
        incident.setElectionId(electionId);
        return incident;
    }

    private BallotStyle style(final UUID electionId, final UUID precinctId, final UUID raceId) {
        final var style = new BallotStyle();
        style.setId(UUID.randomUUID());
        style.setTenantId(this.rows.tenantId);
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

    private void replaceChain(final VoteChain next) {
        next.setLength(2);
        next.setStatus(ChainStatus.CLOSED);
        next.setSignature("sig");
        repository.replace(next);
    }

    private void assertReplaced(final UUID id) {
        final var stored = repository.find(VoteChain.class, id);
        final var chain = stored.orElseThrow();
        assertEquals("def", chain.getHash());
        assertEquals(2, chain.getLength());
        assertEquals(ChainStatus.CLOSED, chain.getStatus());
    }

    private void insertWithoutStatus(final VoteChain chain) {
        chain.setStatus(null);
        repository.insert(chain);
    }

    private void assertNullChain(final UUID id) {
        final var stored = repository.find(VoteChain.class, id);
        final var found = stored.orElseThrow();
        assertNull(found.getCreatedAt());
        assertNull(found.getStatus());
    }

    private void insertOrdered(
        final VoteChain first,
        final VoteChain second,
        final Instant later,
        final Instant earlier
    ) {
        first.setCreatedAt(later);
        second.setCreatedAt(earlier);
        repository.insert(first);
        repository.insert(second);
    }

    private void assertOldest(final UUID id) {
        final var listed = repository.list(VoteChain.class);
        final var oldest = listed.get(0);
        assertEquals(id, oldest.getId());
    }

    private Address address() {
        final var address = new Address();
        address.setId(UUID.randomUUID());
        address.setTenantId(this.rows.tenantId);
        address.setStreet1("1 Main");
        address.setLatitude(Double.valueOf(41.25));
        address.setLongitude(Double.valueOf(-72.5));
        address.setCreatedAt(STAMP);
        return address;
    }

    private Person person() {
        final var person = new Person();
        person.setId(UUID.randomUUID());
        person.setTenantId(this.rows.tenantId);
        person.setFirstName("Ada");
        person.setDateOfBirth(LocalDate.of(1990, 1, 2));
        person.setVersion(Integer.valueOf(7));
        person.setCreatedAt(STAMP);
        return person;
    }

    private RaceCandidate slot() {
        final var slot = new RaceCandidate();
        slot.setId(UUID.randomUUID());
        slot.setTenantId(this.rows.tenantId);
        slot.setWriteIn(true);
        slot.setIncumbent(true);
        slot.setVotesReceived(3);
        slot.setVotePercentage(Double.valueOf(0.5));
        slot.setRank(Integer.valueOf(2));
        slot.setOutcome(Outcome.WON);
        slot.setStatus(RaceCandidateStatus.ACTIVE);
        slot.setCreatedAt(STAMP);
        return slot;
    }

    private void assertAddress(final UUID id) {
        final var stored = repository.find(Address.class, id);
        final var found = stored.orElseThrow();
        final var latitude = found.getLatitude();
        assertEquals("2 Main", found.getStreet1());
        assertEquals(Double.valueOf(42.5), latitude);
        assertEquals(Double.valueOf(-72.5), found.getLongitude());
        assertEquals(STAMP, found.getCreatedAt());
    }

    private void assertPerson(final UUID id) {
        final var stored = repository.find(Person.class, id);
        final var found = stored.orElseThrow();
        assertEquals("Ada", found.getFirstName());
        assertEquals(LocalDate.of(1990, 1, 2), found.getDateOfBirth());
        assertEquals(Integer.valueOf(7), found.getVersion());
        assertEquals(STAMP, found.getCreatedAt());
    }

    private void assertSlot(final UUID id) {
        final var stored = repository.find(RaceCandidate.class, id);
        final var found = stored.orElseThrow();
        assertEquals(true, found.isWriteIn());
        assertEquals(true, found.isIncumbent());
        assertEquals(3, found.getVotesReceived());
        assertEquals(Double.valueOf(0.5), found.getVotePercentage());
        assertEquals(Integer.valueOf(2), found.getRank());
        assertEquals(Outcome.WON, found.getOutcome());
        assertEquals(RaceCandidateStatus.ACTIVE, found.getStatus());
    }
}
