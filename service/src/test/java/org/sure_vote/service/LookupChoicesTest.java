package org.sure_vote.service;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class LookupChoicesTest {
    @Test
    void aFirstPastThePostVoteNamesItsRaceAndCandidate() {
        final var world = SampleElection.fptp();
        final var ballot = world.castOne(world.slotA, "Y");
        final var code = world.codes.get(ballot.getId());
        final var found = world.app.lookup(world.electionId, code);
        final var choices = found.choices();
        final var votes = found.votes();
        final var choice = choices.getFirst();
        final var vote = votes.getFirst();
        assertEquals(vote.getId(), choice.voteId());
        assertEquals(world.raceId, choice.raceId());
        assertEquals("first-past-the-post", choice.method());
        assertEquals("Ada One", choice.candidateName());
        assertFalse(choice.writeIn());
        assertNull(choice.referendumChoice());
    }

    @Test
    void aReferendumVoteCarriesItsChoice() {
        final var world = SampleElection.referendum();
        final var ballot = world.castOne(world.slotB, "Y");
        final var code = world.codes.get(ballot.getId());
        final var found = world.app.lookup(world.electionId, code);
        final var choices = found.choices();
        final var choice = choices.getFirst();
        assertEquals("Levy", choice.raceName());
        assertEquals("referendum", choice.method());
        assertEquals("no", choice.referendumChoice());
    }

    @Test
    void aRankedVoteNamesEachRankedCandidate() {
        final var world = SampleElection.ranked();
        final var ballot = world.castRanked(List.of(world.slotB, world.slotA));
        final var code = world.codes.get(ballot.getId());
        final var found = world.app.lookup(world.electionId, code);
        final var choices = found.choices();
        final var first = choices.getFirst();
        final var stream = choices.stream();
        final var mapped = stream.map(VoteChoice::candidateName);
        final var sorted = mapped.sorted();
        final var names = sorted.toList();
        assertEquals(2, choices.size());
        assertEquals("ranked-choice", first.method());
        assertEquals(List.of("Ada One", "Bea Two"), names);
    }
}
