package org.sure_vote.service;

import java.util.UUID;

import org.sure_vote.entity.Outcome;

final class SlotResult {
    private final UUID id;
    private final int votes;
    private final Integer rank;
    private final Outcome outcome;
    private final boolean writeIn;

    SlotResult(
        final UUID id,
        final int votes,
        final Integer rank,
        final Outcome outcome,
        final boolean writeIn
    ) {
        this.id = id;
        this.votes = votes;
        this.rank = rank;
        this.outcome = outcome;
        this.writeIn = writeIn;
    }

    UUID id() {
        return this.id;
    }

    int votes() {
        return this.votes;
    }

    Integer rank() {
        return this.rank;
    }

    Outcome outcome() {
        return this.outcome;
    }

    boolean writeIn() {
        return this.writeIn;
    }
}
