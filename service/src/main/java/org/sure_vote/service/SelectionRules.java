package org.sure_vote.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.sure_vote.entity.ElectionMethod;
import org.sure_vote.entity.RaceType;

final class SelectionRules {
    static final int MAX_WRITE_IN = 80;

    void check(final List<PreparedVote> votes) {
        this.requireDistinct(votes);
        this.checkRaces(votes);
        this.checkPrimaryParty(votes);
    }

    private void requireDistinct(final List<PreparedVote> votes) {
        final var ids = new HashSet<UUID>();
        for (final var vote : votes) {
            final var added = ids.add(vote.raceCandidateId());
            this.require(added, "A ballot votes once for a candidate");
        }
    }

    private void checkRaces(final List<PreparedVote> votes) {
        final var races = this.byRace(votes);
        final var groups = races.values();
        for (final var group : groups) {
            this.checkRace(group);
        }
    }

    private Map<UUID, List<PreparedVote>> byRace(final List<PreparedVote> votes) {
        final var races = new LinkedHashMap<UUID, List<PreparedVote>>();
        for (final var vote : votes) {
            this.addToRace(races, vote);
        }
        return races;
    }

    private void addToRace(final Map<UUID, List<PreparedVote>> races, final PreparedVote vote) {
        final var existing = races.get(vote.raceId());
        final var group = this.group(existing);
        group.add(vote);
        races.put(vote.raceId(), group);
    }

    private List<PreparedVote> group(final List<PreparedVote> existing) {
        if (existing == null) {
            return new ArrayList<>();
        }
        else {
            return existing;
        }
    }

    private void checkRace(final List<PreparedVote> group) {
        final var first = group.getFirst();
        final var method = first.method();
        if (method == ElectionMethod.FIRST_PAST_THE_POST) {
            this.checkFptp(group);
        }
        else if (method == ElectionMethod.REFERENDUM) {
            this.checkReferendum(group);
        }
        else {
            this.checkRanked(group);
        }
    }

    private void checkFptp(final List<PreparedVote> group) {
        this.require(group.size() <= 1, "A first-past-the-post race allows one vote");
        this.checkFptpValue(group);
    }

    private void checkFptpValue(final List<PreparedVote> group) {
        if (group.isEmpty()) {
            return;
        }
        else {
            final var vote = group.getFirst();
            this.checkFptpVote(vote);
        }
    }

    private void checkFptpVote(final PreparedVote vote) {
        if (vote.writeIn()) {
            this.checkWriteInName(vote.value());
        }
        else {
            this.require("Y".equals(vote.value()), "A first-past-the-post vote uses Y");
        }
    }

    private void checkWriteInName(final String value) {
        if (value == null) {
            throw new ConstraintException("A write-in name is required");
        }
        else {
            final var normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
            final var stripped = value.strip();
            final var length = value.codePointCount(0, value.length());
            this.require(value.equals(normalized), "A write-in name is Unicode NFC");
            this.require(value.equals(stripped), "A write-in name has no extra whitespace");
            this.require(length >= 1 && length <= MAX_WRITE_IN, "A write-in name is 1 to 80 characters");
            this.require(this.noControls(value), "A write-in name has no control characters");
        }
    }

    private boolean noControls(final String value) {
        final var chars = value.toCharArray();
        for (final var ch : chars) {
            if (ch < 0x20 || ch == 0x7f) {
                return false;
            }
        }
        return true;
    }

    private void checkReferendum(final List<PreparedVote> group) {
        this.require(group.size() <= 1, "A referendum allows one vote");
        this.checkReferendumValue(group);
    }

    private void checkReferendumValue(final List<PreparedVote> group) {
        if (group.isEmpty()) {
            return;
        }
        else {
            final var vote = group.getFirst();
            this.require(!vote.writeIn(), "A referendum has no write-in");
            this.require("Y".equals(vote.value()), "A referendum vote uses Y");
            this.require(vote.referendumChoice() != null, "A referendum candidate is yes or no");
        }
    }

    private void checkRanked(final List<PreparedVote> group) {
        final var first = group.getFirst();
        final var ranks = new ArrayList<Integer>();
        this.require(first.appointments() == 1, "Ranked choice elects one winner");
        for (final var vote : group) {
            this.collectRank(ranks, vote);
        }
        this.requireContiguous(this.sorted(ranks), 0);
    }

    private List<Integer> sorted(final List<Integer> ranks) {
        final var copy = new ArrayList<>(ranks);
        copy.sort(Integer::compareTo);
        return copy;
    }

    private void collectRank(final List<Integer> ranks, final PreparedVote vote) {
        final var rank = vote.rank();
        this.require(!vote.writeIn(), "Ranked choice has no write-in");
        this.require(rank >= 1 && rank <= 3, "Ranked-choice ranks are 1, 2, or 3");
        ranks.add(rank);
    }

    private void requireContiguous(final List<Integer> sorted, final int index) {
        if (index >= sorted.size()) {
            return;
        }
        else {
            final var actual = sorted.get(index);
            final var expected = index + 1;
            this.require(actual == expected, "Ranked-choice ranks are contiguous from 1");
            this.requireContiguous(sorted, index + 1);
        }
    }

    private void checkPrimaryParty(final List<PreparedVote> votes) {
        final var parties = new LinkedHashSet<String>();
        for (final var vote : votes) {
            this.collectParty(parties, vote);
        }
        this.require(parties.size() <= 1, "Primary races on one ballot share one party");
    }

    private void collectParty(final Set<String> parties, final PreparedVote vote) {
        if (vote.raceType() == RaceType.PRIMARY) {
            parties.add(this.partyKey(vote.party()));
        }
    }

    private String partyKey(final String party) {
        if (party == null) {
            return "";
        }
        else {
            return party;
        }
    }

    private void require(final boolean condition, final String message) {
        if (!condition) {
            throw new ConstraintException(message);
        }
    }
}
