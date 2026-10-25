package org.sure_vote.service;

import java.util.UUID;

/**
 * Counts for one recording place. At a central office {@code issued} rows are mailed ballots not yet returned,
 * so only {@code received} is compared with the ballots. At a polling place every issued voter went to a
 * machine, so both are.
 */
public record PlaceReconciliation(UUID recordingPlaceId, boolean mail, int issued, int received, int ballots) {
    public boolean matched() {
        if (this.mail) {
            return this.received == this.ballots;
        }
        else {
            return this.issued + this.received == this.ballots;
        }
    }
}
