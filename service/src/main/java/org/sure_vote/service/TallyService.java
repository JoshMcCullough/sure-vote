package org.sure_vote.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    Election close(final UUID electionId, final Map<UUID, String> signatures, final UUID actor) {
        return this.repository.transaction(() -> this.closeLocked(electionId, signatures, actor));
    }

    private Election closeLocked(
        final UUID electionId,
        final Map<UUID, String> signatures,
        final UUID actor
    ) {
        final var election = this.stores.require(Election.class, electionId);
        final var races = this.races(electionId);
        this.stores.require(election.getStatus() == ElectionStatus.ACTIVE, "Only an active election can close");
        this.stores.require(signatures != null, "chain signatures are required");
        this.rejectRanked(races);
        this.closeRaces(election, races, signatures, actor);
        return this.markHistorical(election, actor);
    }

    private void rejectRanked(final List<Race> races) {
        for (final var race : races) {
            this.rejectRankedRace(race);
        }
    }

    private void rejectRankedRace(final Race race) {
        if (race.getMethod() == ElectionMethod.RANKED_CHOICE) {
            throw new ConstraintException("ranked-choice tally is not implemented yet");
        }
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
        final var counted = this.counted(race, slots);
        this.applyAll(slots, counted, actor);
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
