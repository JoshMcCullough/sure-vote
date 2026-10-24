package org.sure_vote.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.sure_vote.entity.Address;
import org.sure_vote.entity.BallotStyle;
import org.sure_vote.entity.Candidate;
import org.sure_vote.entity.DistrictType;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.ElectionMethod;
import org.sure_vote.entity.Identifiable;
import org.sure_vote.entity.Locality;
import org.sure_vote.entity.PrecinctDistrict;
import org.sure_vote.entity.Race;
import org.sure_vote.entity.RaceCandidate;
import org.sure_vote.entity.RecordingDevice;
import org.sure_vote.entity.RecordingDeviceStatus;
import org.sure_vote.entity.ReferendumChoice;
import org.sure_vote.entity.Seat;
import org.sure_vote.entity.Term;
import org.sure_vote.entity.VotingPrecinct;
import org.sure_vote.repository.SureVoteRepository;

final class ElectionDefinitions {
    String payload(final SureVoteRepository repository, final Election election) {
        final var precincts = this.precinctIds(repository, election.getId());
        final var localities = this.precinctLocalities(repository, precincts);
        final var devices = this.deviceArray(repository, election);
        final var races = this.raceArray(repository, election.getId());
        final var style = this.styleArray(repository, election.getId());
        final var districts = this.districtArray(repository, precincts);
        final var localityJson = this.localityArray(localities);
        final var parents = this.parentArray(repository, localities, election.getLocalityId());
        final var algorithm = CanonicalJson.quote(election.getHashAlgorithm());
        final var ballotSchema = CanonicalJson.quote(election.getBallotHashPayloadSchema());
        final var voteSchema = CanonicalJson.quote(election.getVoteHashPayloadSchema());
        final var entrySchema = CanonicalJson.quote(election.getVoteChainEntryHashPayloadSchema());
        final var operatorKey = CanonicalJson.quote(election.getOperatorPublicKey());
        final var importKey = CanonicalJson.quote(election.getImportPublicKey());
        final var viewingKey = CanonicalJson.quote(election.getViewingPublicKey());
        final var fields = new ArrayList<String>();
        fields.add(CanonicalJson.field("electionId", CanonicalJson.uuid(election.getId())));
        fields.add(CanonicalJson.field("hashAlgorithm", algorithm));
        fields.add(CanonicalJson.field("ballotHashPayloadSchema", ballotSchema));
        fields.add(CanonicalJson.field("voteHashPayloadSchema", voteSchema));
        fields.add(CanonicalJson.field("voteChainEntryHashPayloadSchema", entrySchema));
        fields.add(CanonicalJson.field("operatorPublicKey", operatorKey));
        fields.add(CanonicalJson.field("importPublicKey", importKey));
        fields.add(CanonicalJson.field("viewingPublicKey", viewingKey));
        fields.add(CanonicalJson.field("devices", devices));
        fields.add(CanonicalJson.field("races", races));
        fields.add(CanonicalJson.field("ballotStyle", style));
        fields.add(CanonicalJson.field("precinctDistricts", districts));
        fields.add(CanonicalJson.field("precinctLocalities", localityJson));
        fields.add(CanonicalJson.field("localityParents", parents));
        return CanonicalJson.object(fields);
    }

    void validate(final SureVoteRepository repository, final Election election) {
        this.require("SHA-256".equals(election.getHashAlgorithm()), "hashAlgorithm is SHA-256");
        this.require(
            "canonical-json-v1".equals(election.getBallotHashPayloadSchema()),
            "ballot schema is canonical-json-v1"
        );
        this.require(
            "canonical-json-v1".equals(election.getVoteHashPayloadSchema()),
            "vote schema is canonical-json-v1"
        );
        this.require(
            "canonical-json-v1".equals(election.getVoteChainEntryHashPayloadSchema()),
            "chain schema is canonical-json-v1"
        );
        this.require(Digests.isLowerHex(election.getOperatorPublicKey(), 64), "operator public key");
        this.require(Digests.isLowerHex(election.getImportPublicKey(), 64), "import public key");
        this.require(Digests.isLowerHex(election.getViewingPublicKey(), 64), "viewing public key");
        this.validateRaces(repository, election.getId());
        this.validateDevices(repository, election);
        this.validateCoverage(repository, election.getId());
    }

    Set<UUID> deviceIds(final SureVoteRepository repository, final Election election) {
        final var ids = new LinkedHashSet<UUID>();
        final var devices = this.devices(repository, election);
        for (final var device : devices) {
            ids.add(device.getId());
        }
        return ids;
    }

    private void validateRaces(final SureVoteRepository repository, final UUID electionId) {
        final var races = this.races(repository, electionId);
        for (final var race : races) {
            this.validateRace(repository, race);
        }
    }

    private void validateRace(final SureVoteRepository repository, final Race race) {
        final var method = race.getMethod();
        final var slots = this.slots(repository, race.getId());
        final var appointments = this.appointments(repository, race);
        if (method == ElectionMethod.RANKED_CHOICE) {
            this.require(appointments == 1, "Ranked choice elects one winner");
            this.require(this.writeInCount(slots) == 0, "Ranked choice has no write-in");
        }
        else if (method == ElectionMethod.REFERENDUM) {
            this.require(this.referendumShape(slots), "A referendum needs one yes and one no");
        }
        else {
            this.require(this.writeInCount(slots) <= 1, "A race has one write-in slot");
        }
    }

    private int appointments(final SureVoteRepository repository, final Race race) {
        final var term = this.requireRow(repository, Term.class, race.getTermId());
        final var seat = this.requireRow(repository, Seat.class, term.getSeatId());
        return seat.getAppointments();
    }

    private boolean referendumShape(final List<RaceCandidate> slots) {
        final var yes = this.countChoice(slots, ReferendumChoice.YES);
        final var no = this.countChoice(slots, ReferendumChoice.NO);
        final var writeIns = this.writeInCount(slots);
        return slots.size() == 2 && yes == 1 && no == 1 && writeIns == 0;
    }

    private int countChoice(final List<RaceCandidate> slots, final ReferendumChoice choice) {
        var total = 0;
        for (final var slot : slots) {
            total = total + this.choiceMatch(slot, choice);
        }
        return total;
    }

    private int choiceMatch(final RaceCandidate slot, final ReferendumChoice choice) {
        if (slot.getReferendumChoice() == choice) {
            return 1;
        }
        else {
            return 0;
        }
    }

    private int writeInCount(final List<RaceCandidate> slots) {
        var total = 0;
        for (final var slot : slots) {
            total = total + this.writeInOne(slot);
        }
        return total;
    }

    private int writeInOne(final RaceCandidate slot) {
        if (slot.isWriteIn()) {
            return 1;
        }
        else {
            return 0;
        }
    }

    private void validateDevices(final SureVoteRepository repository, final Election election) {
        final var devices = this.devices(repository, election);
        for (final var device : devices) {
            this.require(Digests.isLowerHex(device.getPublicKey(), 64), "device public key");
        }
    }

    private void validateCoverage(final SureVoteRepository repository, final UUID electionId) {
        final var precincts = this.precinctIds(repository, electionId);
        final var districts = this.districts(repository, precincts);
        final var types = this.types(districts);
        for (final var precinct : precincts) {
            this.validatePrecinct(districts, types, precinct);
        }
    }

    private void validatePrecinct(
        final List<PrecinctDistrict> districts,
        final Set<DistrictType> types,
        final UUID precinct
    ) {
        for (final var type : types) {
            final var count = this.countDistrict(districts, precinct, type);
            this.require(count == 1, "Each ballot-style precinct needs one district of each type");
        }
    }

    private int countDistrict(
        final List<PrecinctDistrict> districts,
        final UUID precinct,
        final DistrictType type
    ) {
        var total = 0;
        for (final var row : districts) {
            total = total + this.districtMatch(row, precinct, type);
        }
        return total;
    }

    private int districtMatch(final PrecinctDistrict row, final UUID precinct, final DistrictType type) {
        final var precinctId = row.getVotingPrecinctId();
        final var matches = precinct.equals(precinctId) && row.getDistrictType() == type;
        if (matches) {
            return 1;
        }
        else {
            return 0;
        }
    }

    private Set<DistrictType> types(final List<PrecinctDistrict> districts) {
        final var types = new LinkedHashSet<DistrictType>();
        for (final var row : districts) {
            types.add(row.getDistrictType());
        }
        return types;
    }

    private String raceArray(final SureVoteRepository repository, final UUID electionId) {
        final var races = this.races(repository, electionId);
        final var encoded = new ArrayList<String>();
        for (final var race : races) {
            encoded.add(this.raceJson(repository, race));
        }
        return CanonicalJson.array(encoded);
    }

    private String raceJson(final SureVoteRepository repository, final Race race) {
        final var term = this.requireRow(repository, Term.class, race.getTermId());
        final var seat = this.requireRow(repository, Seat.class, term.getSeatId());
        final var method = race.getMethod();
        final var type = race.getType();
        final var methodWire = CanonicalJson.quote(method.wire());
        final var typeWire = CanonicalJson.quote(type.wire());
        final var appointments = CanonicalJson.number(seat.getAppointments());
        final var candidates = this.candidateArray(repository, race.getId());
        final var fields = new ArrayList<String>();
        fields.add(CanonicalJson.field("raceId", CanonicalJson.uuid(race.getId())));
        fields.add(CanonicalJson.field("method", methodWire));
        fields.add(CanonicalJson.field("type", typeWire));
        fields.add(CanonicalJson.field("appointments", appointments));
        fields.add(CanonicalJson.field("candidates", candidates));
        return CanonicalJson.object(fields);
    }

    private String candidateArray(final SureVoteRepository repository, final UUID raceId) {
        final var slots = this.slots(repository, raceId);
        final var encoded = new ArrayList<String>();
        for (final var slot : slots) {
            encoded.add(this.candidateJson(repository, slot));
        }
        return CanonicalJson.array(encoded);
    }

    private String candidateJson(final SureVoteRepository repository, final RaceCandidate slot) {
        final var candidate = this.requireRow(repository, Candidate.class, slot.getCandidateId());
        final var party = CanonicalJson.nullableString(candidate.getPartyAffiliation());
        final var choice = this.choice(slot.getReferendumChoice());
        final var writeIn = CanonicalJson.bool(slot.isWriteIn());
        final var fields = new ArrayList<String>();
        fields.add(CanonicalJson.field("raceCandidateId", CanonicalJson.uuid(slot.getId())));
        fields.add(CanonicalJson.field("partyAffiliation", party));
        fields.add(CanonicalJson.field("writeIn", writeIn));
        fields.add(CanonicalJson.field("referendumChoice", choice));
        return CanonicalJson.object(fields);
    }

    private String choice(final ReferendumChoice choice) {
        if (choice == null) {
            return "null";
        }
        else {
            return CanonicalJson.quote(choice.wire());
        }
    }

    private String styleArray(final SureVoteRepository repository, final UUID electionId) {
        final var rows = this.styles(repository, electionId);
        final var encoded = new ArrayList<String>();
        for (final var row : rows) {
            encoded.add(this.styleJson(row));
        }
        return CanonicalJson.array(encoded);
    }

    private String styleJson(final BallotStyle row) {
        final var fields = new ArrayList<String>();
        fields.add(CanonicalJson.field("votingPrecinctId", CanonicalJson.uuid(row.getVotingPrecinctId())));
        fields.add(CanonicalJson.field("raceId", CanonicalJson.uuid(row.getRaceId())));
        return CanonicalJson.object(fields);
    }

    private String districtArray(final SureVoteRepository repository, final Set<UUID> precincts) {
        final var rows = this.districts(repository, precincts);
        final var encoded = new ArrayList<String>();
        for (final var row : rows) {
            encoded.add(this.districtJson(row));
        }
        return CanonicalJson.array(encoded);
    }

    private String districtJson(final PrecinctDistrict row) {
        final var type = row.getDistrictType();
        final var typeWire = CanonicalJson.quote(type.wire());
        final var fields = new ArrayList<String>();
        fields.add(CanonicalJson.field("votingPrecinctId", CanonicalJson.uuid(row.getVotingPrecinctId())));
        fields.add(CanonicalJson.field("districtType", typeWire));
        fields.add(CanonicalJson.field("votingDistrictId", CanonicalJson.uuid(row.getVotingDistrictId())));
        return CanonicalJson.object(fields);
    }

    private String localityArray(final List<PrecinctLocality> localities) {
        final var encoded = new ArrayList<String>();
        for (final var locality : localities) {
            encoded.add(this.localityJson(locality));
        }
        return CanonicalJson.array(encoded);
    }

    private String localityJson(final PrecinctLocality locality) {
        final var fields = new ArrayList<String>();
        fields.add(CanonicalJson.field("votingPrecinctId", CanonicalJson.uuid(locality.precinctId)));
        fields.add(CanonicalJson.field("localityId", CanonicalJson.uuid(locality.localityId)));
        return CanonicalJson.object(fields);
    }

    private String parentArray(
        final SureVoteRepository repository,
        final List<PrecinctLocality> localities,
        final UUID electionLocalityId
    ) {
        final var starts = this.starts(localities);
        final var seen = this.ancestors(repository, starts, electionLocalityId);
        final var ids = this.sortedIds(seen);
        final var encoded = new ArrayList<String>();
        for (final var id : ids) {
            encoded.add(this.parentJson(repository, id));
        }
        return CanonicalJson.array(encoded);
    }

    private Set<UUID> starts(final List<PrecinctLocality> localities) {
        final var ids = new LinkedHashSet<UUID>();
        for (final var locality : localities) {
            ids.add(locality.localityId);
        }
        return ids;
    }

    private List<UUID> sortedIds(final Set<UUID> ids) {
        final var copy = new ArrayList<>(ids);
        copy.sort((left, right) -> this.compareIds(left, right));
        return copy;
    }

    private String parentJson(final SureVoteRepository repository, final UUID localityId) {
        final var locality = this.requireRow(repository, Locality.class, localityId);
        final var fields = new ArrayList<String>();
        fields.add(CanonicalJson.field("localityId", CanonicalJson.uuid(localityId)));
        fields.add(CanonicalJson.field("parentLocalityId", CanonicalJson.uuid(locality.getParentId())));
        return CanonicalJson.object(fields);
    }

    private String deviceArray(final SureVoteRepository repository, final Election election) {
        final var devices = this.devices(repository, election);
        final var encoded = new ArrayList<String>();
        for (final var device : devices) {
            encoded.add(this.deviceJson(device));
        }
        return CanonicalJson.array(encoded);
    }

    private String deviceJson(final RecordingDevice device) {
        final var key = CanonicalJson.quote(device.getPublicKey());
        final var fields = new ArrayList<String>();
        fields.add(CanonicalJson.field("deviceId", CanonicalJson.uuid(device.getId())));
        fields.add(CanonicalJson.field("publicKey", key));
        return CanonicalJson.object(fields);
    }

    private List<Race> races(final SureVoteRepository repository, final UUID electionId) {
        final var matched = new ArrayList<Race>();
        final var rows = repository.list(Race.class);
        for (final var race : rows) {
            this.collectRace(matched, race, electionId);
        }
        matched.sort((left, right) -> this.compareIds(left.getId(), right.getId()));
        return matched;
    }

    private void collectRace(final List<Race> matched, final Race race, final UUID electionId) {
        final var raceElection = race.getElectionId();
        final var matches = electionId.equals(raceElection) && race.getDeletedAt() == null;
        if (matches) {
            matched.add(race);
        }
    }

    private List<RaceCandidate> slots(final SureVoteRepository repository, final UUID raceId) {
        final var matched = new ArrayList<RaceCandidate>();
        final var rows = repository.list(RaceCandidate.class);
        for (final var slot : rows) {
            this.collectSlot(matched, slot, raceId);
        }
        matched.sort((left, right) -> this.compareIds(left.getId(), right.getId()));
        return matched;
    }

    private void collectSlot(final List<RaceCandidate> matched, final RaceCandidate slot, final UUID raceId) {
        final var slotRace = slot.getRaceId();
        final var matches = raceId.equals(slotRace) && slot.getDeletedAt() == null;
        if (matches) {
            matched.add(slot);
        }
    }

    private List<BallotStyle> styles(final SureVoteRepository repository, final UUID electionId) {
        final var matched = new ArrayList<BallotStyle>();
        final var rows = repository.list(BallotStyle.class);
        for (final var row : rows) {
            this.collectStyle(matched, row, electionId);
        }
        matched.sort((left, right) -> this.compareStyle(left, right));
        return matched;
    }

    private void collectStyle(final List<BallotStyle> matched, final BallotStyle row, final UUID electionId) {
        final var rowElection = row.getElectionId();
        final var matches = electionId.equals(rowElection) && row.getDeletedAt() == null;
        if (matches) {
            matched.add(row);
        }
    }

    private int compareStyle(final BallotStyle left, final BallotStyle right) {
        final var leftPrecinct = left.getVotingPrecinctId();
        final var rightPrecinct = right.getVotingPrecinctId();
        final var precinct = this.compareIds(leftPrecinct, rightPrecinct);
        if (precinct != 0) {
            return precinct;
        }
        else {
            final var leftRace = left.getRaceId();
            final var rightRace = right.getRaceId();
            return this.compareIds(leftRace, rightRace);
        }
    }

    private List<PrecinctDistrict> districts(final SureVoteRepository repository, final Set<UUID> precincts) {
        final var matched = new ArrayList<PrecinctDistrict>();
        final var rows = repository.list(PrecinctDistrict.class);
        for (final var row : rows) {
            this.collectDistrict(matched, precincts, row);
        }
        matched.sort((left, right) -> this.compareDistrict(left, right));
        return matched;
    }

    private void collectDistrict(
        final List<PrecinctDistrict> matched,
        final Set<UUID> precincts,
        final PrecinctDistrict row
    ) {
        final var precinctId = row.getVotingPrecinctId();
        final var included = precincts.contains(precinctId) && row.getDeletedAt() == null;
        if (included) {
            matched.add(row);
        }
    }

    private int compareDistrict(final PrecinctDistrict left, final PrecinctDistrict right) {
        final var leftPrecinct = left.getVotingPrecinctId();
        final var rightPrecinct = right.getVotingPrecinctId();
        final var precinct = this.compareIds(leftPrecinct, rightPrecinct);
        if (precinct != 0) {
            return precinct;
        }
        else {
            final var leftType = left.getDistrictType();
            final var rightType = right.getDistrictType();
            final var leftWire = leftType.wire();
            final var rightWire = rightType.wire();
            final var type = leftWire.compareTo(rightWire);
            if (type != 0) {
                return type;
            }
            else {
                final var leftDistrict = left.getVotingDistrictId();
                final var rightDistrict = right.getVotingDistrictId();
                return this.compareIds(leftDistrict, rightDistrict);
            }
        }
    }

    private List<RecordingDevice> devices(final SureVoteRepository repository, final Election election) {
        final var matched = new ArrayList<RecordingDevice>();
        final var rows = repository.list(RecordingDevice.class);
        for (final var device : rows) {
            this.collectDevice(matched, election, device);
        }
        matched.sort((left, right) -> this.compareIds(left.getId(), right.getId()));
        return matched;
    }

    private void collectDevice(
        final List<RecordingDevice> matched,
        final Election election,
        final RecordingDevice device
    ) {
        final var electionTenant = election.getTenantId();
        final var deviceTenant = device.getTenantId();
        final var sameTenant = electionTenant.equals(deviceTenant);
        final var active = device.getStatus() == RecordingDeviceStatus.ACTIVE;
        final var kept = device.getDeletedAt() == null;
        if (sameTenant && active && kept) {
            matched.add(device);
        }
    }

    private Set<UUID> precinctIds(final SureVoteRepository repository, final UUID electionId) {
        final var ids = new LinkedHashSet<UUID>();
        final var rows = this.styles(repository, electionId);
        for (final var row : rows) {
            ids.add(row.getVotingPrecinctId());
        }
        return ids;
    }

    private List<PrecinctLocality> precinctLocalities(
        final SureVoteRepository repository,
        final Set<UUID> precincts
    ) {
        final var rows = new ArrayList<PrecinctLocality>();
        for (final var precinctId : precincts) {
            rows.add(this.precinctLocality(repository, precinctId));
        }
        rows.sort((left, right) -> this.compareIds(left.precinctId, right.precinctId));
        return rows;
    }

    private PrecinctLocality precinctLocality(final SureVoteRepository repository, final UUID precinctId) {
        final var precinct = this.requireRow(repository, VotingPrecinct.class, precinctId);
        final var addressId = precinct.getAddressId();
        this.require(addressId != null, "Precinct address is required");
        return this.localityFromAddress(repository, precinctId, addressId);
    }

    private PrecinctLocality localityFromAddress(
        final SureVoteRepository repository,
        final UUID precinctId,
        final UUID addressId
    ) {
        final var address = this.requireRow(repository, Address.class, addressId);
        final var localityId = address.getLocalityId();
        this.require(localityId != null, "Precinct address locality is required");
        return new PrecinctLocality(precinctId, localityId);
    }

    private Set<UUID> ancestors(
        final SureVoteRepository repository,
        final Set<UUID> starts,
        final UUID electionLocalityId
    ) {
        final var seen = new LinkedHashSet<UUID>();
        final var pending = new ArrayList<>(starts);
        this.walk(repository, pending, seen, electionLocalityId);
        return seen;
    }

    private void walk(
        final SureVoteRepository repository,
        final List<UUID> pending,
        final Set<UUID> seen,
        final UUID electionLocalityId
    ) {
        if (pending.isEmpty()) {
            return;
        }
        else {
            final var id = pending.removeFirst();
            this.visit(repository, pending, seen, electionLocalityId, id);
            this.walk(repository, pending, seen, electionLocalityId);
        }
    }

    private void visit(
        final SureVoteRepository repository,
        final List<UUID> pending,
        final Set<UUID> seen,
        final UUID electionLocalityId,
        final UUID id
    ) {
        final var already = seen.contains(id);
        if (already) {
            return;
        }
        else {
            final var locality = this.requireRow(repository, Locality.class, id);
            seen.add(id);
            this.enqueueParent(pending, electionLocalityId, id, locality.getParentId());
        }
    }

    private void enqueueParent(
        final List<UUID> pending,
        final UUID electionLocalityId,
        final UUID id,
        final UUID parentId
    ) {
        final var stop = id.equals(electionLocalityId) || parentId == null;
        if (!stop) {
            pending.add(parentId);
        }
    }

    private int compareIds(final UUID left, final UUID right) {
        final var leftText = left.toString();
        final var rightText = right.toString();
        return leftText.compareTo(rightText);
    }

    private <T extends Identifiable> T requireRow(
        final SureVoteRepository repository,
        final Class<T> type,
        final UUID id
    ) {
        final var found = repository.find(type, id);
        if (found.isEmpty()) {
            throw new ConstraintException("Missing " + type.getSimpleName());
        }
        else {
            return found.get();
        }
    }

    private void require(final boolean condition, final String message) {
        if (!condition) {
            throw new ConstraintException(message);
        }
    }

    private static final class PrecinctLocality {
        private final UUID precinctId;
        private final UUID localityId;

        private PrecinctLocality(final UUID precinctId, final UUID localityId) {
            this.precinctId = precinctId;
            this.localityId = localityId;
        }
    }
}
