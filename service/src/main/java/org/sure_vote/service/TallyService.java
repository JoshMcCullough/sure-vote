package org.sure_vote.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.sure_vote.entity.CanvassStatement;
import org.sure_vote.entity.ChainStatus;
import org.sure_vote.entity.Election;
import org.sure_vote.entity.ElectionMethod;
import org.sure_vote.entity.ElectionStatus;
import org.sure_vote.entity.Outcome;
import org.sure_vote.entity.Race;
import org.sure_vote.entity.RaceCandidate;
import org.sure_vote.entity.Seat;
import org.sure_vote.entity.Term;
import org.sure_vote.entity.Vote;
import org.sure_vote.entity.VoteChain;
import org.sure_vote.repository.SureVoteRepository;

final class TallyService {
    private final SureVoteRepository repository;
    private final Stores stores;

    TallyService(final SureVoteRepository repository, final Stores stores) {
        this.repository = repository;
        this.stores = stores;
    }

    Election close(
        final UUID electionId,
        final Map<UUID, String> signatures,
        final String canvassSignature,
        final UUID actor
    ) {
        return this.repository.transaction(() -> this.closeLocked(electionId, signatures, canvassSignature, actor));
    }

    String canvassPayload(final UUID electionId) {
        final var election = this.stores.require(Election.class, electionId);
        return this.canvassPayload(election);
    }

    private Election closeLocked(
        final UUID electionId,
        final Map<UUID, String> signatures,
        final String canvassSignature,
        final UUID actor
    ) {
        final var election = this.stores.require(Election.class, electionId);
        final var races = this.races(electionId);
        this.stores.require(election.getStatus() == ElectionStatus.ACTIVE, "Only an active election can close");
        this.stores.require(signatures != null, "chain signatures are required");
        this.closeRaces(election, races, signatures, actor);
        this.writeCanvass(election, canvassSignature, actor);
        return this.markHistorical(election, actor);
    }

    private void writeCanvass(final Election election, final String signature, final UUID actor) {
        final var payload = this.canvassPayload(election);
        final var hash = Digests.sha256Hex(payload);
        final var raw = Digests.unhex(hash);
        this.requireCanvassSignature(election, signature, raw);
        this.insertCanvass(election, payload, hash, signature, actor);
    }

    private void requireCanvassSignature(final Election election, final String signature, final byte[] raw) {
        final var signed = this.canvassSigned(election, signature, raw);
        this.stores.require(signature != null, "canvass signature is required");
        this.stores.require(signed, "Operator signature does not match the canvass statement");
    }

    private boolean canvassSigned(final Election election, final String signature, final byte[] raw) {
        if (signature == null) {
            return false;
        }
        else {
            final var key = election.getOperatorPublicKey();
            return Signatures.verify(key, raw, signature);
        }
    }

    private String canvassPayload(final Election election) {
        return CanvassStatements.payload(this.repository, election, this);
    }

    private void insertCanvass(
        final Election election,
        final String payload,
        final String hash,
        final String signature,
        final UUID actor
    ) {
        final var statement = new CanvassStatement();
        statement.setId(UUID.randomUUID());
        statement.setTenantId(election.getTenantId());
        statement.setCreatedAt(Instant.now());
        statement.setCreatedBy(actor);
        statement.setElectionId(election.getId());
        statement.setPayload(payload);
        statement.setHash(hash);
        statement.setSignature(signature);
        this.stores.insert(statement);
    }

    private void closeRaces(
        final Election election,
        final List<Race> races,
        final Map<UUID, String> signatures,
        final UUID actor
    ) {
        for (final var race : races) {
            this.closeRace(election, race, signatures, actor);
        }
    }

    private void closeRace(
        final Election election,
        final Race race,
        final Map<UUID, String> signatures,
        final UUID actor
    ) {
        final var chain = this.chain(race.getId());
        final var signature = signatures.get(race.getId());
        final var raw = Digests.unhex(chain.getHash());
        final var signed = Signatures.verify(election.getOperatorPublicKey(), raw, signature);
        this.stores.require(signature != null, "Operator signature is required for each chain");
        this.stores.require(signed, "Operator signature does not match the chain head");
        this.tally(race, actor);
        this.seal(chain, signature, actor);
    }

    private void tally(final Race race, final UUID actor) {
        final var slots = this.slots(race.getId());
        if (race.getMethod() == ElectionMethod.RANKED_CHOICE) {
            this.tallyRanked(slots, actor);
        }
        else {
            final var counted = this.counted(race, slots);
            this.applyAll(slots, counted, actor);
        }
    }

    List<SlotResult> project(final Race race) {
        final var slots = this.slots(race.getId());
        if (race.getMethod() == ElectionMethod.RANKED_CHOICE) {
            return this.projectRanked(slots);
        }
        else {
            return this.projectPlurality(race, slots);
        }
    }

    private List<SlotResult> projectPlurality(final Race race, final List<RaceCandidate> slots) {
        final var counted = this.counted(race, slots);
        final var results = new ArrayList<SlotResult>();
        for (final var slot : slots) {
            results.add(this.pluralityResult(slot, counted));
        }
        return results;
    }

    private SlotResult pluralityResult(final RaceCandidate slot, final Counted counted) {
        final var votes = this.votesReceived(slot, counted.counts, counted.writeIns);
        final var outcome = this.pluralityOutcome(slot, votes, counted);
        final var rank = this.rank(outcome);
        return new SlotResult(slot.getId(), votes, rank, outcome, slot.isWriteIn());
    }

    private Outcome pluralityOutcome(final RaceCandidate slot, final int votes, final Counted counted) {
        if (slot.isWriteIn()) {
            return null;
        }
        else {
            return this.outcomeFor(votes, counted.options, counted.appointments);
        }
    }

    private List<SlotResult> projectRanked(final List<RaceCandidate> slots) {
        final var firsts = this.firstChoices(slots);
        final var ballots = this.rankedBallots(slots);
        final var outcomes = this.instantRunoff(slots, ballots);
        final var results = new ArrayList<SlotResult>();
        for (final var slot : slots) {
            results.add(this.rankedResult(slot, firsts, outcomes));
        }
        return results;
    }

    private SlotResult rankedResult(
        final RaceCandidate slot,
        final Map<UUID, Integer> firsts,
        final Map<UUID, Outcome> outcomes
    ) {
        final var votes = this.zero(firsts.get(slot.getId()));
        final var outcome = outcomes.get(slot.getId());
        final var rank = this.rank(outcome);
        return new SlotResult(slot.getId(), votes, rank, outcome, false);
    }

    private void tallyRanked(final List<RaceCandidate> slots, final UUID actor) {
        final var firsts = this.firstChoices(slots);
        final var ballots = this.rankedBallots(slots);
        final var outcomes = this.instantRunoff(slots, ballots);
        this.applyRanked(slots, firsts, outcomes, actor);
    }

    private Map<UUID, Integer> firstChoices(final List<RaceCandidate> slots) {
        final var slotMap = this.slotMap(slots);
        final var votes = this.votesFor(slotMap);
        final var counts = new LinkedHashMap<UUID, Integer>();
        for (final var vote : votes) {
            this.addFirst(counts, vote);
        }
        return counts;
    }

    private void addFirst(final Map<UUID, Integer> counts, final Vote vote) {
        if ("1".equals(vote.getValue())) {
            final var id = vote.getRaceCandidateId();
            final var current = counts.get(id);
            counts.put(id, this.increment(current));
        }
    }

    private Map<UUID, List<Preference>> rankedBallots(final List<RaceCandidate> slots) {
        final var slotMap = this.slotMap(slots);
        final var votes = this.votesFor(slotMap);
        final var ballots = new LinkedHashMap<UUID, List<Preference>>();
        for (final var vote : votes) {
            this.addPreference(ballots, vote);
        }
        return ballots;
    }

    private void addPreference(final Map<UUID, List<Preference>> ballots, final Vote vote) {
        final var rank = VoteOrder.rank(vote.getValue());
        if (rank >= 1) {
            final var list = this.preferences(ballots, vote.getBallotId());
            list.add(new Preference(vote.getRaceCandidateId(), rank));
        }
    }

    private List<Preference> preferences(final Map<UUID, List<Preference>> ballots, final UUID ballotId) {
        final var existing = ballots.get(ballotId);
        if (existing != null) {
            return existing;
        }
        else {
            final var created = new ArrayList<Preference>();
            ballots.put(ballotId, created);
            return created;
        }
    }

    private Map<UUID, Outcome> instantRunoff(
        final List<RaceCandidate> slots,
        final Map<UUID, List<Preference>> ballots
    ) {
        final var continuing = this.ids(slots);
        final var eliminated = new LinkedHashSet<UUID>();
        return this.rounds(continuing, eliminated, ballots);
    }

    private LinkedHashSet<UUID> ids(final List<RaceCandidate> slots) {
        final var ids = new LinkedHashSet<UUID>();
        for (final var slot : slots) {
            ids.add(slot.getId());
        }
        return ids;
    }

    private Map<UUID, Outcome> rounds(
        final Set<UUID> continuing,
        final Set<UUID> eliminated,
        final Map<UUID, List<Preference>> ballots
    ) {
        var outcomes = this.oneRound(continuing, eliminated, ballots);
        while (outcomes == null) {
            outcomes = this.oneRound(continuing, eliminated, ballots);
        }
        return outcomes;
    }

    private Map<UUID, Outcome> oneRound(
        final Set<UUID> continuing,
        final Set<UUID> eliminated,
        final Map<UUID, List<Preference>> ballots
    ) {
        if (continuing.isEmpty()) {
            return this.finish(continuing, eliminated, null, List.of());
        }
        else {
            final var counts = this.roundCounts(ballots, continuing);
            final var total = this.total(counts);
            final var winner = this.winner(counts, total);
            return this.afterCount(continuing, eliminated, counts, winner);
        }
    }

    private Map<UUID, Integer> roundCounts(
        final Map<UUID, List<Preference>> ballots,
        final Set<UUID> continuing
    ) {
        final var counts = new LinkedHashMap<UUID, Integer>();
        for (final var id : continuing) {
            counts.put(id, 0);
        }
        this.addChoices(counts, ballots, continuing);
        return counts;
    }

    private void addChoices(
        final Map<UUID, Integer> counts,
        final Map<UUID, List<Preference>> ballots,
        final Set<UUID> continuing
    ) {
        final var lists = ballots.values();
        for (final var preferences : lists) {
            this.addChoice(counts, preferences, continuing);
        }
    }

    private void addChoice(
        final Map<UUID, Integer> counts,
        final List<Preference> preferences,
        final Set<UUID> continuing
    ) {
        final var choice = this.currentChoice(preferences, continuing);
        if (choice != null) {
            final var current = counts.get(choice);
            counts.put(choice, this.increment(current));
        }
    }

    private UUID currentChoice(final List<Preference> preferences, final Set<UUID> continuing) {
        var best = (Preference) null;
        for (final var preference : preferences) {
            best = this.better(best, preference, continuing);
        }
        return this.candidateOf(best);
    }

    private Preference better(
        final Preference best,
        final Preference preference,
        final Set<UUID> continuing
    ) {
        if (!continuing.contains(preference.candidateId)) {
            return best;
        }
        else if (best == null || preference.rank < best.rank) {
            return preference;
        }
        else {
            return best;
        }
    }

    private UUID candidateOf(final Preference preference) {
        if (preference == null) {
            return null;
        }
        else {
            return preference.candidateId;
        }
    }

    private int total(final Map<UUID, Integer> counts) {
        var sum = 0;
        final var values = counts.values();
        for (final var count : values) {
            sum = sum + count;
        }
        return sum;
    }

    private UUID winner(final Map<UUID, Integer> counts, final int total) {
        var found = (UUID) null;
        final var entries = counts.entrySet();
        for (final var entry : entries) {
            final var id = entry.getKey();
            final var count = entry.getValue();
            found = this.pickWinner(found, id, count, total);
        }
        return found;
    }

    private UUID pickWinner(final UUID found, final UUID id, final int count, final int total) {
        final var majority = (long) count * 2 > total;
        if (majority) {
            return id;
        }
        else {
            return found;
        }
    }

    private Map<UUID, Outcome> afterCount(
        final Set<UUID> continuing,
        final Set<UUID> eliminated,
        final Map<UUID, Integer> counts,
        final UUID winner
    ) {
        if (winner != null) {
            return this.finish(continuing, eliminated, winner, List.of());
        }
        else {
            final var lowest = this.lowest(counts);
            return this.afterLowest(continuing, eliminated, lowest);
        }
    }

    private List<UUID> lowest(final Map<UUID, Integer> counts) {
        final var min = this.minimum(counts);
        final var tied = new ArrayList<UUID>();
        final var entries = counts.entrySet();
        for (final var entry : entries) {
            this.collectLowest(tied, entry.getKey(), entry.getValue(), min);
        }
        return tied;
    }

    private int minimum(final Map<UUID, Integer> counts) {
        var min = Integer.MAX_VALUE;
        final var values = counts.values();
        for (final var count : values) {
            min = this.lower(min, count);
        }
        return min;
    }

    private int lower(final int min, final int count) {
        if (count < min) {
            return count;
        }
        else {
            return min;
        }
    }

    private void collectLowest(final List<UUID> tied, final UUID id, final int count, final int min) {
        if (count == min) {
            tied.add(id);
        }
    }

    private Map<UUID, Outcome> afterLowest(
        final Set<UUID> continuing,
        final Set<UUID> eliminated,
        final List<UUID> lowest
    ) {
        if (lowest.size() != 1 || continuing.size() == 1) {
            return this.finish(continuing, eliminated, null, lowest);
        }
        else {
            final var id = lowest.getFirst();
            continuing.remove(id);
            eliminated.add(id);
            return null;
        }
    }

    private Map<UUID, Outcome> finish(
        final Set<UUID> continuing,
        final Set<UUID> eliminated,
        final UUID winner,
        final List<UUID> tied
    ) {
        final var outcomes = new LinkedHashMap<UUID, Outcome>();
        this.markEliminated(outcomes, eliminated);
        this.markContinuing(outcomes, continuing, winner, tied);
        return outcomes;
    }

    private void markEliminated(final Map<UUID, Outcome> outcomes, final Set<UUID> eliminated) {
        for (final var id : eliminated) {
            outcomes.put(id, Outcome.ELIMINATED);
        }
    }

    private void markContinuing(
        final Map<UUID, Outcome> outcomes,
        final Set<UUID> continuing,
        final UUID winner,
        final List<UUID> tied
    ) {
        for (final var id : continuing) {
            this.markOne(outcomes, id, winner, tied);
        }
    }

    private void markOne(
        final Map<UUID, Outcome> outcomes,
        final UUID id,
        final UUID winner,
        final List<UUID> tied
    ) {
        if (id.equals(winner)) {
            outcomes.put(id, Outcome.WON);
        }
        else if (tied.contains(id)) {
            outcomes.put(id, Outcome.TIED);
        }
        else {
            outcomes.put(id, Outcome.LOST);
        }
    }

    private void applyRanked(
        final List<RaceCandidate> slots,
        final Map<UUID, Integer> firsts,
        final Map<UUID, Outcome> outcomes,
        final UUID actor
    ) {
        for (final var slot : slots) {
            this.applyRankedSlot(slot, firsts, outcomes, actor);
        }
    }

    private void applyRankedSlot(
        final RaceCandidate slot,
        final Map<UUID, Integer> firsts,
        final Map<UUID, Outcome> outcomes,
        final UUID actor
    ) {
        final var votes = this.zero(firsts.get(slot.getId()));
        final var outcome = outcomes.get(slot.getId());
        final var next = Copies.raceCandidate(slot);
        next.setVotesReceived(votes);
        next.setVotePercentage(null);
        next.setOutcome(outcome);
        next.setRank(this.rank(outcome));
        next.setUpdatedAt(Instant.now());
        next.setUpdatedBy(actor);
        next.setVersion(this.stores.nextVersion(slot.getVersion()));
        this.stores.replace(next);
    }

    private Counted counted(final Race race, final List<RaceCandidate> slots) {
        final var slotMap = this.slotMap(slots);
        final var votes = this.votesFor(slotMap);
        final var counts = new LinkedHashMap<UUID, Integer>();
        final var writeIns = new LinkedHashMap<String, Integer>();
        this.countVotes(counts, writeIns, slotMap, votes);
        return this.finish(race, slots, counts, writeIns);
    }

    private Counted finish(
        final Race race,
        final List<RaceCandidate> slots,
        final Map<UUID, Integer> counts,
        final Map<String, Integer> writeIns
    ) {
        final var options = this.options(slots, counts, writeIns);
        final var appointments = this.appointments(race);
        return new Counted(counts, writeIns, options, appointments);
    }

    private void applyAll(final List<RaceCandidate> slots, final Counted counted, final UUID actor) {
        for (final var slot : slots) {
            this.apply(slot, counted.counts, counted.writeIns, counted.options, counted.appointments, actor);
        }
    }

    private void countVotes(
        final Map<UUID, Integer> counts,
        final Map<String, Integer> writeIns,
        final Map<UUID, RaceCandidate> slots,
        final List<Vote> votes
    ) {
        for (final var vote : votes) {
            this.countVote(counts, writeIns, slots, vote);
        }
    }

    private void countVote(
        final Map<UUID, Integer> counts,
        final Map<String, Integer> writeIns,
        final Map<UUID, RaceCandidate> slots,
        final Vote vote
    ) {
        final var slot = slots.get(vote.getRaceCandidateId());
        this.stores.require(slot != null, "Vote names a candidate outside the race");
        this.addCount(counts, writeIns, slot, vote);
    }

    private void addCount(
        final Map<UUID, Integer> counts,
        final Map<String, Integer> writeIns,
        final RaceCandidate slot,
        final Vote vote
    ) {
        if (slot.isWriteIn()) {
            final var name = vote.getValue();
            final var current = writeIns.get(name);
            writeIns.put(name, this.increment(current));
        }
        else {
            final var id = slot.getId();
            final var current = counts.get(id);
            counts.put(id, this.increment(current));
        }
    }

    private int increment(final Integer current) {
        if (current == null) {
            return 1;
        }
        else {
            return current + 1;
        }
    }

    private List<Option> options(
        final List<RaceCandidate> slots,
        final Map<UUID, Integer> counts,
        final Map<String, Integer> writeIns
    ) {
        final var options = new ArrayList<Option>();
        for (final var slot : slots) {
            this.addCandidateOption(options, counts, slot);
        }
        this.addWriteInOptions(options, writeIns);
        return options;
    }

    private void addCandidateOption(
        final List<Option> options,
        final Map<UUID, Integer> counts,
        final RaceCandidate slot
    ) {
        if (!slot.isWriteIn()) {
            final var id = slot.getId();
            final var votes = this.zero(counts.get(id));
            options.add(new Option(votes));
        }
    }

    private void addWriteInOptions(final List<Option> options, final Map<String, Integer> writeIns) {
        final var values = writeIns.values();
        for (final var votes : values) {
            options.add(new Option(votes));
        }
    }

    private int appointments(final Race race) {
        if (race.getMethod() == ElectionMethod.REFERENDUM) {
            return 1;
        }
        else {
            final var term = this.stores.require(Term.class, race.getTermId());
            final var seat = this.stores.require(Seat.class, term.getSeatId());
            return seat.getAppointments();
        }
    }

    private void apply(
        final RaceCandidate slot,
        final Map<UUID, Integer> counts,
        final Map<String, Integer> writeIns,
        final List<Option> options,
        final int appointments,
        final UUID actor
    ) {
        final var votes = this.votesReceived(slot, counts, writeIns);
        final var next = Copies.raceCandidate(slot);
        next.setVotesReceived(votes);
        next.setVotePercentage(null);
        this.applyOutcome(next, slot, votes, options, appointments);
        next.setUpdatedAt(Instant.now());
        next.setUpdatedBy(actor);
        next.setVersion(this.stores.nextVersion(slot.getVersion()));
        this.stores.replace(next);
    }

    private int votesReceived(
        final RaceCandidate slot,
        final Map<UUID, Integer> counts,
        final Map<String, Integer> writeIns
    ) {
        if (slot.isWriteIn()) {
            return this.sum(writeIns);
        }
        else {
            return this.zero(counts.get(slot.getId()));
        }
    }

    private int sum(final Map<String, Integer> writeIns) {
        final var values = writeIns.values();
        var total = 0;
        for (final var votes : values) {
            total = total + votes;
        }
        return total;
    }

    private int zero(final Integer count) {
        if (count == null) {
            return 0;
        }
        else {
            return count;
        }
    }

    private void applyOutcome(
        final RaceCandidate next,
        final RaceCandidate slot,
        final int votes,
        final List<Option> options,
        final int appointments
    ) {
        if (slot.isWriteIn()) {
            next.setOutcome(null);
            next.setRank(null);
        }
        else {
            final var outcome = this.outcomeFor(votes, options, appointments);
            next.setOutcome(outcome);
            next.setRank(this.rank(outcome));
        }
    }

    private Integer rank(final Outcome outcome) {
        if (outcome == Outcome.WON) {
            return 1;
        }
        else {
            return null;
        }
    }

    private Outcome outcomeFor(final int votes, final List<Option> options, final int appointments) {
        final var sorted = this.sorted(options);
        return this.outcomeForSorted(votes, sorted, appointments);
    }

    private List<Option> sorted(final List<Option> options) {
        final var copy = new ArrayList<>(options);
        copy.sort((left, right) -> Integer.compare(right.votes, left.votes));
        return copy;
    }

    private Outcome outcomeForSorted(final int votes, final List<Option> sorted, final int appointments) {
        final var threshold = this.threshold(sorted, appointments);
        final var above = this.countAbove(sorted, threshold);
        final var at = this.countAt(sorted, threshold);
        return this.outcome(votes, threshold, above, at, appointments);
    }

    private int threshold(final List<Option> sorted, final int appointments) {
        if (appointments < 1 || sorted.size() < appointments) {
            return 0;
        }
        else {
            final var atSeat = sorted.get(appointments - 1);
            return atSeat.votes;
        }
    }

    private int countAbove(final List<Option> sorted, final int threshold) {
        var total = 0;
        for (final var option : sorted) {
            total = this.addAbove(total, option.votes, threshold);
        }
        return total;
    }

    private int addAbove(final int total, final int votes, final int threshold) {
        if (votes > threshold) {
            return total + 1;
        }
        else {
            return total;
        }
    }

    private int countAt(final List<Option> sorted, final int threshold) {
        var total = 0;
        for (final var option : sorted) {
            total = this.addAt(total, option.votes, threshold);
        }
        return total;
    }

    private int addAt(final int total, final int votes, final int threshold) {
        if (votes == threshold) {
            return total + 1;
        }
        else {
            return total;
        }
    }

    private Outcome outcome(
        final int votes,
        final int threshold,
        final int above,
        final int at,
        final int appointments
    ) {
        final var seatsLeft = appointments - above;
        final var cutoffTied = at != seatsLeft;
        if (votes > threshold) {
            return Outcome.WON;
        }
        else if (votes == threshold && !cutoffTied && seatsLeft > 0) {
            return Outcome.WON;
        }
        else if (votes == threshold && cutoffTied) {
            return Outcome.TIED;
        }
        else {
            return Outcome.LOST;
        }
    }

    private void seal(final VoteChain chain, final String signature, final UUID actor) {
        final var next = Copies.chain(chain);
        next.setStatus(ChainStatus.CLOSED);
        next.setSignature(signature);
        next.setUpdatedAt(Instant.now());
        next.setUpdatedBy(actor);
        next.setVersion(this.stores.nextVersion(chain.getVersion()));
        this.stores.replace(next);
    }

    private Election markHistorical(final Election election, final UUID actor) {
        final var next = Copies.election(election);
        next.setStatus(ElectionStatus.HISTORICAL);
        next.setUpdatedAt(Instant.now());
        next.setUpdatedBy(actor);
        next.setVersion(this.stores.nextVersion(election.getVersion()));
        return this.stores.replace(next);
    }

    private List<Race> races(final UUID electionId) {
        final var matched = new ArrayList<Race>();
        final var rows = this.repository.list(Race.class);
        for (final var race : rows) {
            this.collectRace(matched, race, electionId);
        }
        return matched;
    }

    private void collectRace(final List<Race> matched, final Race race, final UUID electionId) {
        final var raceElection = race.getElectionId();
        final var matches = electionId.equals(raceElection) && race.getDeletedAt() == null;
        if (matches) {
            matched.add(race);
        }
    }

    private List<RaceCandidate> slots(final UUID raceId) {
        final var matched = new ArrayList<RaceCandidate>();
        final var rows = this.repository.list(RaceCandidate.class);
        for (final var slot : rows) {
            this.collectSlot(matched, slot, raceId);
        }
        return matched;
    }

    private void collectSlot(final List<RaceCandidate> matched, final RaceCandidate slot, final UUID raceId) {
        final var slotRace = slot.getRaceId();
        final var matches = raceId.equals(slotRace) && slot.getDeletedAt() == null;
        if (matches) {
            matched.add(slot);
        }
    }

    private Map<UUID, RaceCandidate> slotMap(final List<RaceCandidate> slots) {
        final var map = new LinkedHashMap<UUID, RaceCandidate>();
        for (final var slot : slots) {
            map.put(slot.getId(), slot);
        }
        return map;
    }

    private List<Vote> votesFor(final Map<UUID, RaceCandidate> slots) {
        final var matched = new ArrayList<Vote>();
        final var votes = this.repository.list(Vote.class);
        for (final var vote : votes) {
            this.collectRaceVote(matched, slots, vote);
        }
        return matched;
    }

    private void collectRaceVote(
        final List<Vote> matched,
        final Map<UUID, RaceCandidate> slots,
        final Vote vote
    ) {
        if (slots.containsKey(vote.getRaceCandidateId())) {
            matched.add(vote);
        }
    }

    private VoteChain chain(final UUID raceId) {
        final var matched = new ArrayList<VoteChain>();
        final var rows = this.repository.list(VoteChain.class);
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

    private static final class Preference {
        private final UUID candidateId;
        private final int rank;

        private Preference(final UUID candidateId, final int rank) {
            this.candidateId = candidateId;
            this.rank = rank;
        }
    }

    private static final class Option {
        private final int votes;

        private Option(final int votes) {
            this.votes = votes;
        }
    }

    private static final class Counted {
        private final Map<UUID, Integer> counts;
        private final Map<String, Integer> writeIns;
        private final List<Option> options;
        private final int appointments;

        private Counted(
            final Map<UUID, Integer> counts,
            final Map<String, Integer> writeIns,
            final List<Option> options,
            final int appointments
        ) {
            this.counts = counts;
            this.writeIns = writeIns;
            this.options = options;
            this.appointments = appointments;
        }
    }
}
