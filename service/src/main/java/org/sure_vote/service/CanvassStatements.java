package org.sure_vote.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.sure_vote.entity.Address;
import org.sure_vote.entity.Ballot;
import org.sure_vote.entity.BallotIssuance;
import org.sure_vote.entity.BallotStyle;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.ElectionMethod;
import org.sure_vote.entity.IssuanceStatus;
import org.sure_vote.entity.Locality;
import org.sure_vote.entity.Outcome;
import org.sure_vote.entity.PrecinctDistrict;
import org.sure_vote.entity.Race;
import org.sure_vote.entity.RaceCandidate;
import org.sure_vote.entity.Vote;
import org.sure_vote.entity.VoteChain;
import org.sure_vote.entity.VotingPrecinct;
import org.sure_vote.repository.SureVoteRepository;

final class CanvassStatements {
    private CanvassStatements() {
    }

    static String payload(
        final SureVoteRepository repository,
        final Election election,
        final TallyService tally
    ) {
        final var races = races(repository, election.getId());
        final var ballots = ballots(repository, election.getId());
        final var votes = votesByBallot(repository);
        final var raceJson = raceArray(repository, tally, election, races, ballots, votes);
        final var placeJson = placeArray(repository, election.getId(), ballots);
        final var fields = new ArrayList<String>();
        fields.add(CanonicalJson.field("electionId", CanonicalJson.uuid(election.getId())));
        fields.add(CanonicalJson.field("races", raceJson));
        fields.add(CanonicalJson.field("places", placeJson));
        return CanonicalJson.object(fields);
    }

    private static String raceArray(
        final SureVoteRepository repository,
        final TallyService tally,
        final Election election,
        final List<Race> races,
        final List<Ballot> ballots,
        final Map<UUID, List<Vote>> votes
    ) {
        final var sorted = new ArrayList<>(races);
        final var rows = new ArrayList<String>();
        sorted.sort((left, right) -> compareIds(left.getId(), right.getId()));
        for (final var race : sorted) {
            rows.add(raceJson(repository, tally, election, race, ballots, votes));
        }
        return CanonicalJson.array(rows);
    }

    private static String raceJson(
        final SureVoteRepository repository,
        final TallyService tally,
        final Election election,
        final Race race,
        final List<Ballot> ballots,
        final Map<UUID, List<Vote>> votes
    ) {
        final var results = sorted(tally.project(race));
        final var precincts = precincts(repository, election.getId(), race.getId());
        final var slots = slotMap(repository, race.getId());
        final var counts = precinctCounts(results, slots, race.getMethod(), precincts, ballots, votes);
        final var chain = chain(repository, race.getId());
        final var fields = new ArrayList<String>();
        fields.add(CanonicalJson.field("raceId", CanonicalJson.uuid(race.getId())));
        fields.add(CanonicalJson.field("chainHash", CanonicalJson.quote(chain.getHash())));
        fields.add(CanonicalJson.field("length", CanonicalJson.number(chain.getLength())));
        fields.add(CanonicalJson.field("precincts", precinctArray(results, precincts, counts)));
        fields.add(CanonicalJson.field("districts", districtArray(repository, results, precincts, counts)));
        fields.add(CanonicalJson.field("localities", localityArray(repository, election, results, precincts, counts)));
        fields.add(CanonicalJson.field("results", resultArray(results)));
        return CanonicalJson.object(fields);
    }

    private static List<SlotResult> sorted(final List<SlotResult> results) {
        final var copy = new ArrayList<>(results);
        copy.sort((left, right) -> compareIds(left.id(), right.id()));
        return copy;
    }

    private static String precinctArray(
        final List<SlotResult> results,
        final List<UUID> precincts,
        final Map<UUID, Count> counts
    ) {
        final var sorted = new ArrayList<>(precincts);
        final var rows = new ArrayList<String>();
        sorted.sort((left, right) -> compareIds(left, right));
        for (final var precinct : sorted) {
            rows.add(geography(results, "votingPrecinctId", precinct, counts.get(precinct)));
        }
        return CanonicalJson.array(rows);
    }

    private static String districtArray(
        final SureVoteRepository repository,
        final List<SlotResult> results,
        final List<UUID> precincts,
        final Map<UUID, Count> counts
    ) {
        final var groups = districtGroups(repository, results, precincts, counts);
        final var keys = new ArrayList<>(groups.keySet());
        final var rows = new ArrayList<String>();
        keys.sort((left, right) -> compareDistricts(left, right));
        for (final var key : keys) {
            rows.add(districtJson(results, key, groups.get(key)));
        }
        return CanonicalJson.array(rows);
    }

    private static Map<DistrictKey, Count> districtGroups(
        final SureVoteRepository repository,
        final List<SlotResult> results,
        final List<UUID> precincts,
        final Map<UUID, Count> counts
    ) {
        final var groups = new LinkedHashMap<DistrictKey, Count>();
        final var rows = repository.list(PrecinctDistrict.class);
        for (final var row : rows) {
            addDistrict(groups, results, precincts, counts, row);
        }
        return groups;
    }

    private static void addDistrict(
        final Map<DistrictKey, Count> groups,
        final List<SlotResult> results,
        final List<UUID> precincts,
        final Map<UUID, Count> counts,
        final PrecinctDistrict row
    ) {
        final var precinct = row.getVotingPrecinctId();
        final var kept = row.getDeletedAt() == null && precincts.contains(precinct);
        if (kept) {
            final var type = row.getDistrictType();
            final var key = new DistrictKey(type.wire(), row.getVotingDistrictId());
            final var count = group(groups, results, key);
            addCount(count, counts.get(precinct));
        }
    }

    private static String districtJson(final List<SlotResult> results, final DistrictKey key, final Count count) {
        final var fields = new ArrayList<String>();
        fields.add(CanonicalJson.field("districtType", CanonicalJson.quote(key.type)));
        fields.add(CanonicalJson.field("votingDistrictId", CanonicalJson.uuid(key.id)));
        fields.add(CanonicalJson.field("ballots", CanonicalJson.number(count.ballots)));
        fields.add(CanonicalJson.field("undervotes", CanonicalJson.number(count.undervotes)));
        fields.add(CanonicalJson.field("candidates", candidateArray(results, count)));
        return CanonicalJson.object(fields);
    }

    private static String localityArray(
        final SureVoteRepository repository,
        final Election election,
        final List<SlotResult> results,
        final List<UUID> precincts,
        final Map<UUID, Count> counts
    ) {
        final var localities = localityCounts(repository, election, results, precincts, counts);
        final var keys = new ArrayList<>(localities.keySet());
        final var rows = new ArrayList<String>();
        keys.sort((left, right) -> compareIds(left, right));
        for (final var key : keys) {
            rows.add(geography(results, "localityId", key, localities.get(key)));
        }
        return CanonicalJson.array(rows);
    }

    private static Map<UUID, Count> localityCounts(
        final SureVoteRepository repository,
        final Election election,
        final List<SlotResult> results,
        final List<UUID> precincts,
        final Map<UUID, Count> counts
    ) {
        final var localities = localityMap(repository);
        final var totals = new LinkedHashMap<UUID, Count>();
        for (final var precinct : precincts) {
            addLocalities(totals, results, localities, election, precinct, counts.get(precinct));
        }
        return totals;
    }

    private static void addLocalities(
        final Map<UUID, Count> totals,
        final List<SlotResult> results,
        final Map<UUID, Locality> localities,
        final Election election,
        final UUID precinct,
        final Count count
    ) {
        final var chain = localityChain(localities, election, precinctLocality(localities, precinct));
        for (final var locality : chain) {
            final var total = group(totals, results, locality);
            addCount(total, count);
        }
    }

    private static UUID precinctLocality(final Map<UUID, Locality> localities, final UUID precinct) {
        final var addressId = addressId(precinct, localities);
        return localityOf(localities, addressId);
    }

    private static List<UUID> localityChain(
        final Map<UUID, Locality> localities,
        final Election election,
        final UUID start
    ) {
        final var chain = new ArrayList<UUID>();
        return walk(chain, localities, election.getLocalityId(), start);
    }

    private static List<UUID> walk(
        final List<UUID> chain,
        final Map<UUID, Locality> localities,
        final UUID electionLocality,
        final UUID current
    ) {
        if (current == null || chain.contains(current)) {
            return chain;
        }
        else {
            chain.add(current);
            return walkAfter(chain, localities, electionLocality, current);
        }
    }

    private static List<UUID> walkAfter(
        final List<UUID> chain,
        final Map<UUID, Locality> localities,
        final UUID electionLocality,
        final UUID current
    ) {
        if (current.equals(electionLocality)) {
            return chain;
        }
        else {
            final var parent = parentOf(localities, current);
            return walk(chain, localities, electionLocality, parent);
        }
    }

    private static String geography(
        final List<SlotResult> results,
        final String idKey,
        final UUID id,
        final Count count
    ) {
        final var fields = new ArrayList<String>();
        fields.add(CanonicalJson.field(idKey, CanonicalJson.uuid(id)));
        fields.add(CanonicalJson.field("ballots", CanonicalJson.number(count.ballots)));
        fields.add(CanonicalJson.field("undervotes", CanonicalJson.number(count.undervotes)));
        fields.add(CanonicalJson.field("candidates", candidateArray(results, count)));
        return CanonicalJson.object(fields);
    }

    private static String candidateArray(final List<SlotResult> results, final Count count) {
        final var rows = new ArrayList<String>();
        for (final var result : results) {
            rows.add(candidateJson(result, count));
        }
        return CanonicalJson.array(rows);
    }

    private static String candidateJson(final SlotResult result, final Count count) {
        final var votes = count.votes.get(result.id());
        final var names = count.names.get(result.id());
        final var fields = new ArrayList<String>();
        fields.add(CanonicalJson.field("raceCandidateId", CanonicalJson.uuid(result.id())));
        fields.add(CanonicalJson.field("votes", CanonicalJson.number(zero(votes))));
        fields.add(CanonicalJson.field("writeIns", writeIns(names)));
        return CanonicalJson.object(fields);
    }

    private static String writeIns(final Map<String, Integer> names) {
        if (names == null || names.isEmpty()) {
            return "[]";
        }
        else {
            final var keys = new ArrayList<>(names.keySet());
            keys.sort((left, right) -> left.compareTo(right));
            return writeInArray(names, keys);
        }
    }

    private static String writeInArray(final Map<String, Integer> names, final List<String> keys) {
        final var rows = new ArrayList<String>();
        for (final var key : keys) {
            rows.add(writeInJson(key, names.get(key)));
        }
        return CanonicalJson.array(rows);
    }

    private static String writeInJson(final String value, final Integer votes) {
        final var fields = new ArrayList<String>();
        fields.add(CanonicalJson.field("value", CanonicalJson.quote(value)));
        fields.add(CanonicalJson.field("votes", CanonicalJson.number(zero(votes))));
        return CanonicalJson.object(fields);
    }

    private static String resultArray(final List<SlotResult> results) {
        final var rows = new ArrayList<String>();
        for (final var result : results) {
            rows.add(resultJson(result));
        }
        return CanonicalJson.array(rows);
    }

    private static String resultJson(final SlotResult result) {
        final var fields = new ArrayList<String>();
        fields.add(CanonicalJson.field("raceCandidateId", CanonicalJson.uuid(result.id())));
        fields.add(CanonicalJson.field("votes", CanonicalJson.number(result.votes())));
        fields.add(CanonicalJson.field("rank", rank(result.rank())));
        fields.add(CanonicalJson.field("outcome", outcome(result.outcome())));
        return CanonicalJson.object(fields);
    }

    private static String rank(final Integer rank) {
        if (rank == null) {
            return "null";
        }
        else {
            return CanonicalJson.number(rank);
        }
    }

    private static String outcome(final Outcome outcome) {
        if (outcome == null) {
            return "null";
        }
        else {
            final var wire = outcome.wire();
            return CanonicalJson.quote(wire);
        }
    }

    private static String placeArray(
        final SureVoteRepository repository,
        final UUID electionId,
        final List<Ballot> ballots
    ) {
        final var counts = placeCounts(repository, electionId, ballots);
        final var keys = new ArrayList<>(counts.keySet());
        final var rows = new ArrayList<String>();
        keys.sort((left, right) -> compareIds(left, right));
        for (final var key : keys) {
            rows.add(placeJson(key, counts.get(key)));
        }
        return CanonicalJson.array(rows);
    }

    private static String placeJson(final UUID placeId, final PlaceCount count) {
        final var fields = new ArrayList<String>();
        fields.add(CanonicalJson.field("recordingPlaceId", CanonicalJson.uuid(placeId)));
        fields.add(CanonicalJson.field("acceptedIssuances", CanonicalJson.number(count.issuances)));
        fields.add(CanonicalJson.field("ballots", CanonicalJson.number(count.ballots)));
        return CanonicalJson.object(fields);
    }

    private static Map<UUID, Count> precinctCounts(
        final List<SlotResult> results,
        final Map<UUID, RaceCandidate> slots,
        final ElectionMethod method,
        final List<UUID> precincts,
        final List<Ballot> ballots,
        final Map<UUID, List<Vote>> votes
    ) {
        final var counts = new LinkedHashMap<UUID, Count>();
        for (final var precinct : precincts) {
            counts.put(precinct, blank(results));
        }
        for (final var ballot : ballots) {
            addBallot(counts, results, slots, method, precincts, votes, ballot);
        }
        return counts;
    }

    private static void addBallot(
        final Map<UUID, Count> counts,
        final List<SlotResult> results,
        final Map<UUID, RaceCandidate> slots,
        final ElectionMethod method,
        final List<UUID> precincts,
        final Map<UUID, List<Vote>> votes,
        final Ballot ballot
    ) {
        final var precinct = ballot.getVotingPrecinctId();
        if (precincts.contains(precinct)) {
            final var count = counts.get(precinct);
            final var ballotVotes = votes.get(ballot.getId());
            count.ballots = count.ballots + 1;
            addUndervote(count, slots, ballotVotes);
            addVotes(count, results, slots, method, ballotVotes);
        }
    }

    private static void addUndervote(
        final Count count,
        final Map<UUID, RaceCandidate> slots,
        final List<Vote> votes
    ) {
        if (!voted(slots, votes)) {
            count.undervotes = count.undervotes + 1;
        }
    }

    private static boolean voted(final Map<UUID, RaceCandidate> slots, final List<Vote> votes) {
        if (votes == null) {
            return false;
        }
        else {
            return anySlot(slots, votes);
        }
    }

    private static boolean anySlot(final Map<UUID, RaceCandidate> slots, final List<Vote> votes) {
        for (final var vote : votes) {
            if (slots.containsKey(vote.getRaceCandidateId())) {
                return true;
            }
        }
        return false;
    }

    private static void addVotes(
        final Count count,
        final List<SlotResult> results,
        final Map<UUID, RaceCandidate> slots,
        final ElectionMethod method,
        final List<Vote> votes
    ) {
        if (votes != null) {
            for (final var vote : votes) {
                addVote(count, results, slots, method, vote);
            }
        }
    }

    private static void addVote(
        final Count count,
        final List<SlotResult> results,
        final Map<UUID, RaceCandidate> slots,
        final ElectionMethod method,
        final Vote vote
    ) {
        final var slot = slots.get(vote.getRaceCandidateId());
        if (slot != null && counts(method, vote)) {
            final var current = count.votes.get(slot.getId());
            count.votes.put(slot.getId(), zero(current) + 1);
            addWriteIn(count, results, slot, vote);
        }
    }

    private static boolean counts(final ElectionMethod method, final Vote vote) {
        if (method == ElectionMethod.RANKED_CHOICE) {
            return "1".equals(vote.getValue());
        }
        else {
            return true;
        }
    }

    private static void addWriteIn(
        final Count count,
        final List<SlotResult> results,
        final RaceCandidate slot,
        final Vote vote
    ) {
        if (writeIn(results, slot.getId())) {
            final var names = namesOf(count, slot.getId());
            final var current = names.get(vote.getValue());
            names.put(vote.getValue(), zero(current) + 1);
        }
    }

    private static boolean writeIn(final List<SlotResult> results, final UUID id) {
        for (final var result : results) {
            final var slotId = result.id();
            if (slotId.equals(id) && result.writeIn()) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Integer> namesOf(final Count count, final UUID id) {
        final var existing = count.names.get(id);
        if (existing != null) {
            return existing;
        }
        else {
            final var created = new LinkedHashMap<String, Integer>();
            count.names.put(id, created);
            return created;
        }
    }

    private static Map<UUID, PlaceCount> placeCounts(
        final SureVoteRepository repository,
        final UUID electionId,
        final List<Ballot> ballots
    ) {
        final var counts = new LinkedHashMap<UUID, PlaceCount>();
        final var issuances = repository.list(BallotIssuance.class);
        for (final var ballot : ballots) {
            addPlaceBallot(counts, ballot.getRecordingPlaceId());
        }
        for (final var issuance : issuances) {
            addIssuance(counts, electionId, issuance);
        }
        return present(counts);
    }

    private static void addPlaceBallot(final Map<UUID, PlaceCount> counts, final UUID placeId) {
        final var count = place(counts, placeId);
        count.ballots = count.ballots + 1;
    }

    private static void addIssuance(
        final Map<UUID, PlaceCount> counts,
        final UUID electionId,
        final BallotIssuance issuance
    ) {
        final var matches = electionId.equals(issuance.getElectionId());
        final var accepted = issuance.getStatus() == IssuanceStatus.ACCEPTED;
        final var kept = issuance.getDeletedAt() == null;
        if (matches && accepted && kept) {
            final var count = place(counts, issuance.getRecordingPlaceId());
            count.issuances = count.issuances + 1;
        }
    }

    private static Map<UUID, PlaceCount> present(final Map<UUID, PlaceCount> counts) {
        final var kept = new LinkedHashMap<UUID, PlaceCount>();
        final var entries = counts.entrySet();
        for (final var entry : entries) {
            keepPlace(kept, entry.getKey(), entry.getValue());
        }
        return kept;
    }

    private static void keepPlace(final Map<UUID, PlaceCount> kept, final UUID id, final PlaceCount count) {
        if (count.ballots > 0 || count.issuances > 0) {
            kept.put(id, count);
        }
    }

    private static PlaceCount place(final Map<UUID, PlaceCount> counts, final UUID id) {
        final var existing = counts.get(id);
        if (existing != null) {
            return existing;
        }
        else {
            final var created = new PlaceCount();
            counts.put(id, created);
            return created;
        }
    }

    private static Count group(
        final Map<DistrictKey, Count> groups,
        final List<SlotResult> results,
        final DistrictKey key
    ) {
        final var existing = groups.get(key);
        if (existing != null) {
            return existing;
        }
        else {
            final var created = blank(results);
            groups.put(key, created);
            return created;
        }
    }

    private static Count group(final Map<UUID, Count> groups, final List<SlotResult> results, final UUID key) {
        final var existing = groups.get(key);
        if (existing != null) {
            return existing;
        }
        else {
            final var created = blank(results);
            groups.put(key, created);
            return created;
        }
    }

    private static void addCount(final Count into, final Count extra) {
        into.ballots = into.ballots + extra.ballots;
        into.undervotes = into.undervotes + extra.undervotes;
        addNumbers(into.votes, extra.votes);
        addNameMaps(into.names, extra.names);
    }

    private static void addNumbers(final Map<UUID, Integer> into, final Map<UUID, Integer> extra) {
        final var entries = extra.entrySet();
        for (final var entry : entries) {
            final var current = into.get(entry.getKey());
            into.put(entry.getKey(), zero(current) + entry.getValue());
        }
    }

    private static void addNameMaps(
        final Map<UUID, Map<String, Integer>> into,
        final Map<UUID, Map<String, Integer>> extra
    ) {
        final var entries = extra.entrySet();
        for (final var entry : entries) {
            addStrings(mapOf(into, entry.getKey()), entry.getValue());
        }
    }

    private static Map<String, Integer> mapOf(final Map<UUID, Map<String, Integer>> into, final UUID id) {
        final var existing = into.get(id);
        if (existing != null) {
            return existing;
        }
        else {
            final var created = new LinkedHashMap<String, Integer>();
            into.put(id, created);
            return created;
        }
    }

    private static void addStrings(final Map<String, Integer> into, final Map<String, Integer> extra) {
        final var entries = extra.entrySet();
        for (final var entry : entries) {
            final var current = into.get(entry.getKey());
            into.put(entry.getKey(), zero(current) + entry.getValue());
        }
    }

    private static Count blank(final List<SlotResult> results) {
        final var count = new Count();
        for (final var result : results) {
            count.votes.put(result.id(), 0);
        }
        return count;
    }

    private static int zero(final Integer value) {
        if (value == null) {
            return 0;
        }
        else {
            return value;
        }
    }

    private static List<Race> races(final SureVoteRepository repository, final UUID electionId) {
        final var matched = new ArrayList<Race>();
        final var rows = repository.list(Race.class);
        for (final var race : rows) {
            keepRace(matched, race, electionId);
        }
        return matched;
    }

    private static void keepRace(final List<Race> matched, final Race race, final UUID electionId) {
        final var matches = electionId.equals(race.getElectionId()) && race.getDeletedAt() == null;
        if (matches) {
            matched.add(race);
        }
    }

    private static List<Ballot> ballots(final SureVoteRepository repository, final UUID electionId) {
        final var matched = new ArrayList<Ballot>();
        final var rows = repository.list(Ballot.class);
        for (final var ballot : rows) {
            keepBallot(matched, ballot, electionId);
        }
        return matched;
    }

    private static void keepBallot(final List<Ballot> matched, final Ballot ballot, final UUID electionId) {
        if (electionId.equals(ballot.getElectionId())) {
            matched.add(ballot);
        }
    }

    private static Map<UUID, List<Vote>> votesByBallot(final SureVoteRepository repository) {
        final var votes = new LinkedHashMap<UUID, List<Vote>>();
        final var rows = repository.list(Vote.class);
        for (final var vote : rows) {
            final var ballotId = vote.getBallotId();
            final var list = voteList(votes, ballotId);
            list.add(vote);
        }
        return votes;
    }

    private static List<Vote> voteList(final Map<UUID, List<Vote>> votes, final UUID ballotId) {
        final var existing = votes.get(ballotId);
        if (existing != null) {
            return existing;
        }
        else {
            final var created = new ArrayList<Vote>();
            votes.put(ballotId, created);
            return created;
        }
    }

    private static List<UUID> precincts(
        final SureVoteRepository repository,
        final UUID electionId,
        final UUID raceId
    ) {
        final var matched = new ArrayList<UUID>();
        final var rows = repository.list(BallotStyle.class);
        for (final var row : rows) {
            keepPrecinct(matched, row, electionId, raceId);
        }
        return matched;
    }

    private static void keepPrecinct(
        final List<UUID> matched,
        final BallotStyle row,
        final UUID electionId,
        final UUID raceId
    ) {
        final var matches = electionId.equals(row.getElectionId()) && raceId.equals(row.getRaceId());
        final var kept = row.getDeletedAt() == null && !matched.contains(row.getVotingPrecinctId());
        if (matches && kept) {
            matched.add(row.getVotingPrecinctId());
        }
    }

    private static Map<UUID, RaceCandidate> slotMap(final SureVoteRepository repository, final UUID raceId) {
        final var slots = new LinkedHashMap<UUID, RaceCandidate>();
        final var rows = repository.list(RaceCandidate.class);
        for (final var slot : rows) {
            keepSlot(slots, slot, raceId);
        }
        return slots;
    }

    private static void keepSlot(final Map<UUID, RaceCandidate> slots, final RaceCandidate slot, final UUID raceId) {
        final var matches = raceId.equals(slot.getRaceId()) && slot.getDeletedAt() == null;
        if (matches) {
            slots.put(slot.getId(), slot);
        }
    }

    private static VoteChain chain(final SureVoteRepository repository, final UUID raceId) {
        final var matched = new ArrayList<VoteChain>();
        final var rows = repository.list(VoteChain.class);
        for (final var row : rows) {
            keepChain(matched, row, raceId);
        }
        if (matched.size() != 1) {
            throw new ConstraintException("A race has one vote chain");
        }
        else {
            return matched.getFirst();
        }
    }

    private static void keepChain(final List<VoteChain> matched, final VoteChain row, final UUID raceId) {
        if (raceId.equals(row.getRaceId())) {
            matched.add(row);
        }
    }

    private static Map<UUID, Locality> localityMap(final SureVoteRepository repository) {
        final var localities = new LinkedHashMap<UUID, Locality>();
        final var rows = repository.list(Locality.class);
        final var precincts = repository.list(VotingPrecinct.class);
        final var addresses = repository.list(Address.class);
        for (final var row : rows) {
            localities.put(row.getId(), row);
        }
        rememberPrecincts(localities, precincts, addresses);
        return localities;
    }

    private static void rememberPrecincts(
        final Map<UUID, Locality> localities,
        final List<VotingPrecinct> precincts,
        final List<Address> addresses
    ) {
        for (final var precinct : precincts) {
            rememberPrecinct(localities, addresses, precinct);
        }
    }

    private static void rememberPrecinct(
        final Map<UUID, Locality> localities,
        final List<Address> addresses,
        final VotingPrecinct precinct
    ) {
        final var address = findAddress(addresses, precinct.getAddressId());
        if (address != null) {
            final var marker = new Locality();
            marker.setParentId(address.getLocalityId());
            localities.put(precinct.getId(), marker);
        }
    }

    private static Address findAddress(final List<Address> addresses, final UUID id) {
        for (final var address : addresses) {
            if (id.equals(address.getId())) {
                return address;
            }
        }
        return null;
    }

    private static UUID addressId(final UUID precinct, final Map<UUID, Locality> localities) {
        final var marker = localities.get(precinct);
        if (marker == null) {
            return null;
        }
        else {
            return marker.getParentId();
        }
    }

    private static UUID localityOf(final Map<UUID, Locality> localities, final UUID addressLocality) {
        return addressLocality;
    }

    private static UUID parentOf(final Map<UUID, Locality> localities, final UUID id) {
        final var locality = localities.get(id);
        if (locality == null) {
            return null;
        }
        else {
            return locality.getParentId();
        }
    }

    private static int compareIds(final UUID left, final UUID right) {
        final var leftText = left.toString();
        final var rightText = right.toString();
        return leftText.compareTo(rightText);
    }

    private static int compareDistricts(final DistrictKey left, final DistrictKey right) {
        final var type = left.type.compareTo(right.type);
        if (type != 0) {
            return type;
        }
        else {
            return compareIds(left.id, right.id);
        }
    }

    private static final class Count {
        private int ballots;
        private int undervotes;
        private final Map<UUID, Integer> votes = new LinkedHashMap<>();
        private final Map<UUID, Map<String, Integer>> names = new LinkedHashMap<>();
    }

    private static final class PlaceCount {
        private int ballots;
        private int issuances;
    }

    private static final class DistrictKey {
        private final String type;
        private final UUID id;

        private DistrictKey(final String type, final UUID id) {
            this.type = type;
            this.id = id;
        }

        @Override
        public boolean equals(final Object other) {
            if (other instanceof DistrictKey key) {
                return this.type.equals(key.type) && this.id.equals(key.id);
            }
            else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            final var typeHash = this.type.hashCode();
            final var idHash = this.id.hashCode();
            return typeHash * 31 + idHash;
        }
    }
}
