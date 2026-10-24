package org.sure_vote.service;

import java.util.UUID;

public final class DemoSelection {
    private final UUID raceCandidateId;
    private final String value;

    public DemoSelection(final UUID raceCandidateId, final String value) {
        this.raceCandidateId = raceCandidateId;
        this.value = value;
    }

    public UUID raceCandidateId() {
        return this.raceCandidateId;
    }

    public String value() {
        return this.value;
    }
}
