package org.sure_vote.service;

import java.util.UUID;

public record PlaceReconciliation(UUID recordingPlaceId, int issued, int received, int ballots) {
    public boolean matched() {
        return this.issued + this.received == this.ballots;
    }
}
