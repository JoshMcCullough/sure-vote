package org.sure_vote.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.sure_vote.entity.AssignmentStatus;
import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.BallotIssuance;
import org.sure_vote.entity.BallotStyle;
import org.sure_vote.entity.BallotType;
import org.sure_vote.entity.Candidate;
import org.sure_vote.entity.ChainStatus;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.ElectionStatus;
import org.sure_vote.entity.ElectionVotingPrecinctAssignment;
import org.sure_vote.entity.IssuanceStatus;
import org.sure_vote.entity.Person;
import org.sure_vote.entity.Race;
import org.sure_vote.entity.RaceCandidate;
import org.sure_vote.entity.RecordingDevice;
import org.sure_vote.entity.RecordingDeviceKind;
import org.sure_vote.entity.RecordingDeviceStatus;
import org.sure_vote.entity.RecordingPlace;
import org.sure_vote.entity.RecordingPlaceKind;
import org.sure_vote.entity.Seat;
import org.sure_vote.entity.Term;
import org.sure_vote.entity.Vote;
import org.sure_vote.entity.VoteChain;
import org.sure_vote.entity.VoteChainEntry;
import org.sure_vote.repository.SureVoteRepository;

final class RecordingService {
    private final SureVoteRepository repository;
    private final Stores stores;
    private final ElectionDefinitions definitions;
    private final SelectionRules rules = new SelectionRules();

    RecordingService(
        final SureVoteRepository repository,
        final Stores stores,
        final ElectionDefinitions definitions
    ) {
        this.repository = repository;
        this.stores = stores;
        this.definitions = definitions;
    }

    Ballot importBallot(final ImportBallot request) {
        return this.repository.transaction(() -> this.importLocked(request));
    }

    private Ballot importLocked(final ImportBallot request) {
        final var context = this.checked(request);
        final var prepared = this.prepare(context.election, request);
        final var ballot = this.storeBallot(context, request, prepared);
        this.issueMail(request);
        return ballot;
    }

    private ImportContext checked(final ImportBallot request) {
        final var election = this.stores.require(Election.class, request.electionId());
        final var device = this.stores.require(RecordingDevice.class, request.recordingDeviceId());
        final var place = this.stores.require(RecordingPlace.class, request.recordingPlaceId());
        final var deviceIds = this.definitions.deviceIds(this.repository, election);
        final var payload = this.definitions.payload(this.repository, election);
        final var hash = Digests.sha256Hex(payload);
        final var stored = election.getHash();
        final var start = election.getVotingStart();
        final var end = election.getVotingEnd();
        final var timestamp = request.timestamp();
        final var inside = timestamp != null && !timestamp.isBefore(start) && !timestamp.isAfter(end);
        final var electionTenant = election.getTenantId();
        final var deviceTenant = device.getTenantId();
        final var placeTenant = place.getTenantId();
        this.requireType(request.type());
        this.requireKind(request.type(), device, place);
        this.stores.sameTenant(request.tenantId(), electionTenant, "Election");
        this.stores.requireActiveTenant(electionTenant);
        this.stores.sameTenant(electionTenant, deviceTenant, "Recording device");
        this.stores.sameTenant(electionTenant, placeTenant, "Recording place");
        this.stores.require(election.getStatus() == ElectionStatus.ACTIVE, "Election is not active");
        this.stores.require(inside, "Ballot timestamp is outside the voting window");
        this.stores.require(
            Digests.isLowerHex(request.voterAccessHash(), 64),
            "voterAccessHash must be lowercase SHA-256 hex"
        );
        this.stores.require(device.getStatus() == RecordingDeviceStatus.ACTIVE, "Recording device is not active");
        this.stores.require(device.getDeletedAt() == null, "Recording device is deleted");
        this.stores.require(deviceIds.contains(request.recordingDeviceId()), "Device is not in the signed definition");
        this.stores.require(hash.equals(stored), "Election definition does not match the signed hash");
        this.stores.require(request.votes() != null, "votes are required");
        this.stores.require(request.recordedBy() != null, "recordedBy is required");
        this.requireMailActor(request);
        this.requireAssignment(request.electionId(), request.votingPrecinctId());
        return new ImportContext(election, device);
    }

    private void requireType(final BallotType type) {
        final var allowed = type == BallotType.IN_PERSON
            || type == BallotType.MAIL_IN
            || type == BallotType.ABSENTEE;
        if (!allowed) {
            throw new ConstraintException("online ballots are not accepted");
        }
    }

    private void requireKind(
        final BallotType type,
        final RecordingDevice device,
        final RecordingPlace place
    ) {
        if (type == BallotType.IN_PERSON) {
            this.requireInPerson(device, place);
        }
        else {
            this.requireMailPlace(device, place);
        }
    }

    private void requireInPerson(final RecordingDevice device, final RecordingPlace place) {
        final var machine = device.getKind() == RecordingDeviceKind.POLLING_PLACE_MACHINE;
        final var pollingPlace = place.getKind() == RecordingPlaceKind.POLLING_PLACE;
        this.stores.require(machine, "recording device kind does not match the ballot type");
        this.stores.require(pollingPlace, "recording place kind does not match the ballot type");
    }

    private void requireMailPlace(final RecordingDevice device, final RecordingPlace place) {
        final var workstation = device.getKind() == RecordingDeviceKind.CENTRAL_WORKSTATION;
        final var office = place.getKind() == RecordingPlaceKind.CENTRAL_OFFICE;
        this.stores.require(workstation, "recording device kind does not match the ballot type");
        this.stores.require(office, "recording place kind does not match the ballot type");
    }

    private void requireMailActor(final ImportBallot request) {
        if (request.type() == BallotType.IN_PERSON) {
            this.requireNoVoter(request);
        }
        else {
            this.requireMailVoter(request);
        }
    }

    private void requireNoVoter(final ImportBallot request) {
        if (request.personId() != null) {
            throw new ConstraintException("an in-person ballot has no voter");
        }
    }

    private void requireMailVoter(final ImportBallot request) {
        final var personId = request.personId();
        final var recordedBy = request.recordedBy();
        final var actorId = request.actorId();
        if (personId == null) {
            throw new ConstraintException("a mail-in ballot requires the voter");
        }
        else if (!recordedBy.equals(actorId)) {
            throw new ConstraintException("mail-in recordedBy is the staff principal");
        }
    }

    private void issueMail(final ImportBallot request) {
        if (request.type() == BallotType.MAIL_IN || request.type() == BallotType.ABSENTEE) {
            this.insertIssuance(request);
        }
    }

    private void insertIssuance(final ImportBallot request) {
        final var person = this.stores.require(Person.class, request.personId());
        final var personTenant = person.getTenantId();
        final var issuance = new BallotIssuance();
        this.stores.sameTenant(request.tenantId(), personTenant, "Person");
        this.stores.require(person.getDeletedAt() == null, "Person is deleted");
        issuance.setId(UUID.randomUUID());
        issuance.setTenantId(request.tenantId());
        issuance.setPersonId(request.personId());
        issuance.setElectionId(request.electionId());
        issuance.setRecordingPlaceId(request.recordingPlaceId());
        issuance.setStatus(IssuanceStatus.ACCEPTED);
        issuance.setAcceptedAt(Instant.now());
        this.stores.add(issuance, request.actorId());
    }

    private void requireAssignment(final UUID electionId, final UUID precinctId) {
        final var rows = this.repository.list(ElectionVotingPrecinctAssignment.class);
        final var matched = new ArrayList<ElectionVotingPrecinctAssignment>();
        for (final var row : rows) {
            this.collectAssignment(matched, row, electionId, precinctId);
        }
        this.stores.require(!matched.isEmpty(), "Precinct is not assigned to the election");
    }

    private void collectAssignment(
        final List<ElectionVotingPrecinctAssignment> matched,
        final ElectionVotingPrecinctAssignment row,
        final UUID electionId,
        final UUID precinctId
    ) {
        final var rowElection = row.getElectionId();
        final var rowPrecinct = row.getVotingPrecinctId();
        final var matches = electionId.equals(rowElection) && precinctId.equals(rowPrecinct);
        final var current = row.getAssignmentStatus() == AssignmentStatus.CURRENT;
        final var kept = row.getDeletedAt() == null;
        if (matches && current && kept) {
            matched.add(row);
        }
    }

    private List<PreparedVote> prepare(final Election election, final ImportBallot request) {
        final var drafted = this.drafts(election, request);
        final var ordered = this.ordered(drafted);
        return this.hashVotes(request, ordered);
    }

    private List<PreparedVote> drafts(final Election election, final ImportBallot request) {
        final var drafted = new ArrayList<PreparedVote>();
        final var votes = request.votes();
        for (final var vote : votes) {
            drafted.add(this.draft(election, request, vote));
        }
        this.rules.check(drafted);
        return drafted;
    }

    private List<PreparedVote> ordered(final List<PreparedVote> drafted) {
        final var copy = new ArrayList<>(drafted);
        copy.sort((left, right) -> PreparedVote.order(left, right));
        return copy;
    }

    private List<PreparedVote> hashVotes(final ImportBallot request, final List<PreparedVote> ordered) {
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
        final var hashed = new ArrayList<PreparedVote>();
        for (final var vote : ordered) {
            hashed.add(vote.withHash(binding));
        }
        return hashed;
    }

    private PreparedVote draft(final Election election, final ImportBallot request, final ImportVote vote) {
        final var slot = this.stores.require(RaceCandidate.class, vote.raceCandidateId());
        final var race = this.stores.require(Race.class, slot.getRaceId());
        final var candidate = this.stores.require(Candidate.class, slot.getCandidateId());
        final var term = this.stores.require(Term.class, race.getTermId());
        final var seat = this.stores.require(Seat.class, term.getSeatId());
        final var electionId = election.getId();
        final var raceElection = race.getElectionId();
        final var nonce = vote.nonce();
        final var nonceLength = nonce == null ? 0 : nonce.length();
        this.stores.require(electionId.equals(raceElection), "The race is in another election");
        this.stores.sameTenant(request.tenantId(), slot.getTenantId(), "Race candidate");
        this.stores.require(slot.getDeletedAt() == null, "Race candidate is deleted");
        this.stores.require(nonceLength >= 1 && nonceLength <= 64, "nonce must be 1 to 64 characters");
        this.stores.require(vote.entryTimestamp() != null, "entry timestamp is required");
        this.stores.require(vote.signature() != null, "vote signature is required");
        this.stores.require(vote.entrySignature() != null, "entry signature is required");
        this.requireStyle(request.electionId(), request.votingPrecinctId(), race.getId());
        return new PreparedVote(
            race.getId(),
            slot.getId(),
            vote.value(),
            VoteOrder.rank(vote.value()),
            seat.getAppointments(),
            race.getMethod(),
            race.getType(),
            candidate.getPartyAffiliation(),
            slot.isWriteIn(),
            slot.getReferendumChoice(),
            vote.signature(),
            vote.entryTimestamp(),
            nonce,
            vote.entrySignature(),
            null,
            null
        );
    }

    private void requireStyle(final UUID electionId, final UUID precinctId, final UUID raceId) {
        final var rows = this.repository.list(BallotStyle.class);
        final var matched = new ArrayList<BallotStyle>();
        for (final var row : rows) {
            this.collectStyle(matched, row, electionId, precinctId, raceId);
        }
        this.stores.require(!matched.isEmpty(), "The race is not in the precinct ballot style");
    }

    private void collectStyle(
        final List<BallotStyle> matched,
        final BallotStyle row,
        final UUID electionId,
        final UUID precinctId,
        final UUID raceId
    ) {
        final var rowElection = row.getElectionId();
        final var rowPrecinct = row.getVotingPrecinctId();
        final var rowRace = row.getRaceId();
        final var matches = electionId.equals(rowElection) && precinctId.equals(rowPrecinct) && raceId.equals(rowRace);
        final var kept = row.getDeletedAt() == null;
        if (matches && kept) {
            matched.add(row);
        }
    }

    private Ballot storeBallot(
        final ImportContext context,
        final ImportBallot request,
        final List<PreparedVote> prepared
    ) {
        final var ballot = this.signedBallot(context, request, prepared);
        this.storeVotes(context.election, request, ballot, prepared);
        return ballot;
    }

    private Ballot signedBallot(
        final ImportContext context,
        final ImportBallot request,
        final List<PreparedVote> prepared
    ) {
        final var device = context.device;
        final var publicKey = device.getPublicKey();
        final var type = request.type();
        final var typeWire = type.wire();
        final var hashes = this.hashes(prepared);
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
        final var signed = Signatures.verify(publicKey, raw, request.signature());
        this.verifyVotes(device, prepared);
        this.stores.require(signed, "Device signature does not match the ballot");
        return this.insertBallot(request, type, payload, hash);
    }

    private void verifyVotes(final RecordingDevice device, final List<PreparedVote> prepared) {
        for (final var vote : prepared) {
            this.verifyVote(device, vote);
        }
    }

    private void verifyVote(final RecordingDevice device, final PreparedVote vote) {
        final var publicKey = device.getPublicKey();
        final var raw = Digests.unhex(vote.hash());
        final var signed = Signatures.verify(publicKey, raw, vote.signature());
        this.stores.require(signed, "Device signature does not match a vote");
    }

    private List<String> hashes(final List<PreparedVote> prepared) {
        final var hashes = new ArrayList<String>();
        for (final var vote : prepared) {
            hashes.add(vote.hash());
        }
        return hashes;
    }

    private Ballot insertBallot(
        final ImportBallot request,
        final BallotType type,
        final String payload,
        final String hash
    ) {
        final var ballot = new Ballot();
        ballot.setId(UUID.randomUUID());
        ballot.setTenantId(request.tenantId());
        ballot.setCreatedAt(Instant.now());
        ballot.setCreatedBy(request.recordedBy());
        ballot.setElectionId(request.electionId());
        ballot.setVotingPrecinctId(request.votingPrecinctId());
        ballot.setType(type);
        ballot.setRecordingDeviceId(request.recordingDeviceId());
        ballot.setRecordingPlaceId(request.recordingPlaceId());
        ballot.setRecordedBy(request.recordedBy());
        ballot.setVoterAccessHash(request.voterAccessHash());
        ballot.setPayload(payload);
        ballot.setHash(hash);
        ballot.setSignature(request.signature());
        ballot.setTimestamp(request.timestamp());
        return this.stores.insert(ballot);
    }

    private void storeVotes(
        final Election election,
        final ImportBallot request,
        final Ballot ballot,
        final List<PreparedVote> prepared
    ) {
        for (final var vote : prepared) {
            this.storeVote(election, request, ballot, vote);
        }
    }

    private void storeVote(
        final Election election,
        final ImportBallot request,
        final Ballot ballot,
        final PreparedVote prepared
    ) {
        final var vote = this.insertVote(request, ballot, prepared);
        this.append(election, request, prepared, vote);
    }

    private Vote insertVote(final ImportBallot request, final Ballot ballot, final PreparedVote prepared) {
        final var vote = new Vote();
        vote.setId(UUID.randomUUID());
        vote.setTenantId(request.tenantId());
        vote.setCreatedAt(Instant.now());
        vote.setCreatedBy(request.recordedBy());
        vote.setBallotId(ballot.getId());
        vote.setRaceCandidateId(prepared.raceCandidateId());
        vote.setValue(prepared.value());
        vote.setPayload(prepared.payload());
        vote.setHash(prepared.hash());
        vote.setSignature(prepared.signature());
        return this.stores.insert(vote);
    }

    private void append(
        final Election election,
        final ImportBallot request,
        final PreparedVote prepared,
        final Vote vote
    ) {
        final var chain = this.chain(prepared.raceId());
        final var entry = this.signedEntry(election, request, chain, prepared, vote);
        this.advance(chain, entry, request.actorId());
    }

    private VoteChainEntry signedEntry(
        final Election election,
        final ImportBallot request,
        final VoteChain chain,
        final PreparedVote prepared,
        final Vote vote
    ) {
        final var previous = this.previous(chain);
        final var index = chain.getLength();
        final var payload = Payloads.entry(
            vote.getHash(),
            previous,
            index,
            prepared.entryTimestamp(),
            prepared.nonce()
        );
        final var hash = Digests.sha256Hex(payload);
        final var raw = Digests.unhex(hash);
        final var signed = Signatures.verify(election.getImportPublicKey(), raw, prepared.entrySignature());
        this.stores.require(chain.getStatus() == ChainStatus.ACTIVE, "Vote chain is not active");
        this.stores.require(signed, "Import signature does not match the chain entry");
        return this.insertEntry(request, chain, vote, prepared, payload, hash, index);
    }

    private String previous(final VoteChain chain) {
        if (chain.getLength() == 0) {
            return null;
        }
        else {
            return chain.getHash();
        }
    }

    private VoteChainEntry insertEntry(
        final ImportBallot request,
        final VoteChain chain,
        final Vote vote,
        final PreparedVote prepared,
        final String payload,
        final String hash,
        final int index
    ) {
        final var entry = new VoteChainEntry();
        entry.setId(UUID.randomUUID());
        entry.setTenantId(request.tenantId());
        entry.setCreatedAt(Instant.now());
        entry.setCreatedBy(request.actorId());
        entry.setVoteChainId(chain.getId());
        entry.setVoteId(vote.getId());
        entry.setPreviousEntryId(chain.getLatestEntryId());
        entry.setPayload(payload);
        entry.setHash(hash);
        entry.setSignature(prepared.entrySignature());
        entry.setIndex(index);
        entry.setTimestamp(prepared.entryTimestamp());
        entry.setNonce(prepared.nonce());
        return this.stores.insert(entry);
    }

    private void advance(final VoteChain chain, final VoteChainEntry entry, final UUID actor) {
        final var next = Copies.chain(chain);
        final var version = this.stores.nextVersion(chain.getVersion());
        next.setLatestEntryId(entry.getId());
        next.setLength(chain.getLength() + 1);
        next.setHash(entry.getHash());
        next.setSignature(null);
        next.setUpdatedAt(Instant.now());
        next.setUpdatedBy(actor);
        next.setVersion(version);
        this.setInitial(chain, next, entry);
        this.stores.replace(next);
    }

    private void setInitial(final VoteChain chain, final VoteChain next, final VoteChainEntry entry) {
        if (chain.getLength() == 0) {
            next.setInitialEntryId(entry.getId());
        }
    }

    private VoteChain chain(final UUID raceId) {
        final var rows = this.repository.list(VoteChain.class);
        final var matched = new ArrayList<VoteChain>();
        for (final var row : rows) {
            this.collectChain(matched, row, raceId);
        }
        this.stores.require(matched.size() == 1, "A race has one vote chain");
        return matched.getFirst();
    }

    private void collectChain(final List<VoteChain> matched, final VoteChain row, final UUID raceId) {
        final var rowRace = row.getRaceId();
        if (raceId.equals(rowRace)) {
            matched.add(row);
        }
    }

    private static final class ImportContext {
        private final Election election;
        private final RecordingDevice device;

        private ImportContext(final Election election, final RecordingDevice device) {
            this.election = election;
            this.device = device;
        }
    }
}
